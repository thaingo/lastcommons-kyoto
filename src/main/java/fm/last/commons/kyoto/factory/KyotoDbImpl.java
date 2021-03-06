/*
 * Copyright 2012 Last.fm
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package fm.last.commons.kyoto.factory;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kyotocabinet.DB;
import kyotocabinet.Error;
import kyotocabinet.FileProcessor;
import fm.last.commons.kyoto.Atomicity;
import fm.last.commons.kyoto.DbType;
import fm.last.commons.kyoto.KyotoCursor;
import fm.last.commons.kyoto.KyotoDb;
import fm.last.commons.kyoto.KyotoFileProcessor;
import fm.last.commons.kyoto.MergeType;
import fm.last.commons.kyoto.ReadOnlyStringVisitor;
import fm.last.commons.kyoto.ReadOnlyVisitor;
import fm.last.commons.kyoto.Synchronization;
import fm.last.commons.kyoto.WritableStringVisitor;
import fm.last.commons.kyoto.WritableVisitor;
import fm.last.commons.kyoto.factory.ErrorHandler.ErrorSource;

class KyotoDbImpl implements KyotoDb {

  private final DB delegate;
  private final String descriptor;
  private final Set<Mode> modes;
  private final DbType dbType;
  private final File file;
  private final ErrorHandler errorHandler;
  private volatile boolean open;
  private String encoding;

  KyotoDbImpl(DbType dbType, final DB delegate, String descriptor, Set<Mode> modes, File file) {
    this.dbType = dbType;
    this.delegate = delegate;
    this.descriptor = descriptor;
    this.modes = modes;
    this.file = file;
    errorHandler = new ErrorHandler(new ErrorSource() {
      @Override
      public Error getError() {
        return delegate.error();
      }
    });
    open = false;
    encoding = "UTF-8";
  }

  @Override
  public void accept(byte[] key, ReadOnlyVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.accept(key, new ReadOnlyVisitorAdapter(visitor), AccessType.READ_ONLY.value()));
  }

  @Override
  public void accept(byte[][] keys, ReadOnlyVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.accept_bulk(keys, new ReadOnlyVisitorAdapter(visitor),
        AccessType.READ_ONLY.value()));
  }

  @Override
  public void accept(String key, ReadOnlyStringVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.accept(stringToByteArray(key), new ReadOnlyStringVisitorAdapter(visitor, this),
        AccessType.READ_ONLY.value()));
  }

  @Override
  public void accept(List<String> keys, ReadOnlyStringVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.accept_bulk(stringListTo2DByteArray(keys), new ReadOnlyStringVisitorAdapter(
        visitor, this), AccessType.READ_ONLY.value()));
  }

  @Override
  public void accept(byte[] key, WritableVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.accept(key, new WritableVisitorAdapter(visitor), AccessType.READ_WRITE.value()));
  }

  @Override
  public void accept(byte[][] keys, WritableVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.accept_bulk(keys, new WritableVisitorAdapter(visitor),
        AccessType.READ_WRITE.value()));
  }

  @Override
  public void accept(String key, WritableStringVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.accept(stringToByteArray(key), new WritableStringVisitorAdapter(visitor, this),
        AccessType.READ_WRITE.value()));
  }

  @Override
  public void accept(List<String> keys, WritableStringVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.accept_bulk(stringListTo2DByteArray(keys), new WritableStringVisitorAdapter(
        visitor, this), AccessType.READ_WRITE.value()));
  }

  @Override
  public boolean putIfAbsent(byte[] key, byte[] value) {
    return errorHandler.wrapBooleanCall(delegate.add(key, value));
  }

  @Override
  public boolean putIfAbsent(String key, String value) {
    return errorHandler.wrapBooleanCall(delegate.add(key, value));
  }

  @Override
  public void append(byte[] key, byte[] value) {
    errorHandler.wrapVoidCall(delegate.append(key, value));
  }

  @Override
  public void append(String key, String value) {
    errorHandler.wrapVoidCall(delegate.append(key, value));
  }

  @Override
  public void begin(Synchronization synchronization) {
    errorHandler.wrapVoidCall(delegate.begin_transaction(synchronization.value()),
        "Could not begin transaction with synchronization: " + synchronization);
  }

  @Override
  public void clear() {
    errorHandler.wrapVoidCall(delegate.clear());
  }

  @Override
  public synchronized void close() throws IOException {
    if (!open) {
      throw new IOException("Connection already closed: " + this);
    }
    errorHandler.wrapVoidIoCall(delegate.close(), "Could not close db: " + descriptor);
    open = false;
  }

  @Override
  public boolean compareAndSwap(byte[] key, byte[] oldValue, byte[] newValue) {
    return errorHandler.wrapBooleanCall(delegate.cas(key, oldValue, newValue));
  }

  @Override
  public boolean compareAndSwap(String key, String oldValue, String newValue) {
    return errorHandler.wrapBooleanCall(delegate.cas(key, oldValue, newValue));
  }

  @Override
  public void copyTo(File destination) throws IOException {
    errorHandler.wrapVoidIoCall(delegate.copy(destination.getAbsolutePath()),
        "Could not copy db to " + destination.getAbsolutePath());
  }

  @Override
  public long recordCount() {
    return errorHandler.wrapLongCall(delegate.count(), -1);
  }

  @Override
  public KyotoCursor cursor() {
    return new CursorAdapter(delegate.cursor());
  }

  @Override
  public void dumpSnapshotTo(File destination) throws IOException {
    errorHandler.wrapVoidIoCall(!delegate.dump_snapshot(destination.getAbsolutePath()), "Could not dump to snapshot: "
        + destination.getAbsolutePath());
  }

  @Override
  public void commit() {
    errorHandler.wrapVoidCall(delegate.end_transaction(true), "Could not commit transaction");
  }

  @Override
  public void rollback() {
    errorHandler.wrapVoidCall(delegate.end_transaction(false), "Could not rollback transaction");
  }

  @Override
  public byte[] get(byte[] key) {
    return errorHandler.wrapObjectCall(delegate.get(key));
  }

  @Override
  public byte[][] get(byte[][] keys, Atomicity atomicity) {
    return errorHandler.wrapObjectCall(delegate.get_bulk(keys, atomicity.value()));
  }

  @Override
  public Map<String, String> get(List<String> keys, Atomicity atomicity) {
    return errorHandler.wrapObjectCall(delegate.get_bulk(keys, atomicity.value()));
  }

  @Override
  public String get(String key) {
    return errorHandler.wrapObjectCall(delegate.get(key));
  }

  @Override
  public File getFile() {
    return file;
  }

  @Override
  public DbType getType() {
    return dbType;
  }

  @Override
  public double increment(byte[] key, double delta) {
    return errorHandler.wrapDoubleCall(delegate.increment_double(key, delta), Double.NaN);
  }

  @Override
  public long increment(byte[] key, long delta) {
    return errorHandler.wrapLongCall(delegate.increment(key, delta), Long.MIN_VALUE);
  }

  @Override
  public double increment(String key, double delta) {
    return errorHandler.wrapDoubleCall(delegate.increment_double(key, delta), Double.NaN);
  }

  @Override
  public long increment(String key, long delta) {
    return errorHandler.wrapLongCall(delegate.increment(key, delta), Long.MIN_VALUE);
  }

  @Override
  public void iterate(ReadOnlyVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.iterate(new ReadOnlyVisitorAdapter(visitor), AccessType.READ_ONLY.value()));
  }

  @Override
  public void iterate(ReadOnlyStringVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.iterate(new ReadOnlyStringVisitorAdapter(visitor, this),
        AccessType.READ_ONLY.value()));
  }

  @Override
  public void iterate(WritableVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.iterate(new WritableVisitorAdapter(visitor), AccessType.READ_WRITE.value()));
  }

  @Override
  public void iterate(WritableStringVisitor visitor) {
    errorHandler.wrapVoidCall(delegate.iterate(new WritableStringVisitorAdapter(visitor, this),
        AccessType.READ_WRITE.value()));
  }

  @Override
  public void loadSnapshotFrom(File source) throws IOException {
    errorHandler.wrapVoidIoCall(delegate.load_snapshot(source.getAbsolutePath()), "Could not load snapshot from: "
        + source.getAbsolutePath());
  }

  @Override
  public List<String> matchKeysByPrefix(String prefix, long limit) {
    return errorHandler.wrapObjectCall(delegate.match_prefix(prefix, limit));
  }

  @Override
  public List<String> matchKeysByPrefix(String prefix) {
    return errorHandler.wrapObjectCall(delegate.match_prefix(prefix, NO_LIMIT));
  }

  @Override
  public List<String> matchKeysByRegex(String regex, long limit) {
    return errorHandler.wrapObjectCall(delegate.match_regex(regex, limit));
  }

  @Override
  public List<String> matchKeysByRegex(String regex) {
    return errorHandler.wrapObjectCall(delegate.match_regex(regex, NO_LIMIT));
  }

  @Override
  public void mergeWith(MergeType mergeType, KyotoDb... dbs) {
    DB[] internal = new DB[dbs.length];
    for (int index = 0; index < dbs.length; index++) {
      internal[index] = ((KyotoDbImpl) dbs[index]).getDelegate();

    }
    errorHandler.wrapVoidCall(delegate.merge(internal, mergeType.value()),
        "Could not merge DBs: " + Arrays.toString(dbs));
  }

  @Override
  public synchronized void open() throws IOException {
    if (open) {
      throw new IllegalStateException("Connection already open: " + this);
    }
    errorHandler.wrapVoidIoCall(delegate.open(descriptor, buildModeMask()), "Could not open database: " + descriptor);
    open = true;
  }

  @Override
  public boolean remove(byte[] key) {
    return errorHandler.wrapBooleanCall(delegate.remove(key));
  }

  @Override
  public long remove(byte[][] keys, Atomicity atomicity) {
    return errorHandler.wrapLongCall(delegate.remove_bulk(keys, atomicity.value()), -1);
  }

  @Override
  public long remove(List<String> keys, Atomicity atomicity) {
    return errorHandler.wrapLongCall(delegate.remove_bulk(keys, atomicity.value()), -1);
  }

  @Override
  public boolean remove(String key) {
    return errorHandler.wrapBooleanCall(delegate.remove(key));
  }

  @Override
  public boolean replace(byte[] key, byte[] newValue) {
    return errorHandler.wrapBooleanCall(delegate.replace(key, newValue));
  }

  @Override
  public boolean replace(String key, String newValue) {
    return errorHandler.wrapBooleanCall(delegate.replace(key, newValue));
  }

  @Override
  public void set(byte[] key, byte[] value) {
    errorHandler.wrapVoidCall(delegate.set(key, value));
  }

  @Override
  public long set(byte[][] keyValues, Atomicity atomicity) {
    return errorHandler.wrapLongCall(delegate.set_bulk(keyValues, atomicity.value()), -1);
  }

  @Override
  public long set(Map<String, String> keyValues, Atomicity atomicity) {
    return errorHandler.wrapLongCall(delegate.set_bulk(keyValues, atomicity.value()), -1);
  }

  @Override
  public void set(String key, String value) {
    errorHandler.wrapVoidCall(delegate.set(key, value));
  }

  @Override
  public long sizeInBytes() {
    return errorHandler.wrapLongCall(delegate.size(), -1);
  }

  @Override
  public Map<String, String> status() {
    return errorHandler.wrapObjectCall(delegate.status());
  }

  @Override
  public void synchronize(Synchronization synchronization, KyotoFileProcessor fileProcessor) {
    FileProcessor adapted = null;
    if (fileProcessor != null) {
      adapted = new FileProcessorAdapter(fileProcessor);
    }
    errorHandler.wrapVoidCall(delegate.synchronize(synchronization.value(), adapted), "Could not " + synchronization
        + " synchronize DB " + descriptor + " with " + fileProcessor);
  }

  @Override
  public void setEncoding(String encoding) {
    errorHandler.wrapVoidCall(delegate.tune_encoding(encoding), "Could not set encoding: " + encoding);
    this.encoding = encoding;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("KyotoDbImpl [dbType=");
    builder.append(dbType);
    builder.append(", descriptor=");
    builder.append(descriptor);
    builder.append(", modes=");
    builder.append(modes);
    builder.append(", delegate=[");
    builder.append(delegate);
    builder.append("], file=");
    builder.append(file);
    builder.append("]");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((dbType == null) ? 0 : dbType.hashCode());
    result = prime * result + ((descriptor == null) ? 0 : descriptor.hashCode());
    result = prime * result + ((file == null) ? 0 : file.hashCode());
    result = prime * result + ((modes == null) ? 0 : modes.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    KyotoDbImpl other = (KyotoDbImpl) obj;
    if (dbType != other.dbType) {
      return false;
    }
    if (descriptor == null) {
      if (other.descriptor != null) {
        return false;
      }
    } else if (!descriptor.equals(other.descriptor)) {
      return false;
    }
    if (file == null) {
      if (other.file != null) {
        return false;
      }
    } else if (!file.equals(other.file)) {
      return false;
    }
    if (modes == null) {
      if (other.modes != null) {
        return false;
      }
    } else if (!modes.equals(other.modes)) {
      return false;
    }
    return true;
  }

  DB getDelegate() {
    return delegate;
  }

  String byteArrayToString(byte[] value) {
    if (value == null) {
      return null;
    }
    try {
      return new String(value, encoding);
    } catch (UnsupportedEncodingException e) {
      return new String(value);
    }
  }

  byte[] stringToByteArray(String str) {
    if (str == null) {
      return null;
    }
    try {
      return str.getBytes(encoding);
    } catch (UnsupportedEncodingException e) {
      return str.getBytes();
    }
  }

  private byte[][] stringListTo2DByteArray(List<String> values) {
    if (values == null) {
      return null;
    }
    byte[][] byteArr = new byte[values.size()][];
    int count = 0;
    for (String value : values) {
      byteArr[count++] = stringToByteArray(value);
    }
    return byteArr;
  }

  private int buildModeMask() {
    int mask = 0;
    for (Mode mode : modes) {
      mask |= mode.value();
    }
    return mask;
  }

}
