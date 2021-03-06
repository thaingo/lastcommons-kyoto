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

import java.io.IOException;

import kyotocabinet.Cursor;
import kyotocabinet.Error;
import fm.last.commons.kyoto.CursorStep;
import fm.last.commons.kyoto.KyotoCursor;
import fm.last.commons.kyoto.ReadOnlyVisitor;
import fm.last.commons.kyoto.WritableVisitor;
import fm.last.commons.kyoto.factory.ErrorHandler.ErrorSource;

class CursorAdapter implements KyotoCursor {

  private final Cursor delegate;
  private final ErrorHandler errorHandler;

  CursorAdapter(final Cursor delegate) {
    this.delegate = delegate;
    errorHandler = new ErrorHandler(new ErrorSource() {
      @Override
      public Error getError() {
        return delegate.error();
      }
    });
  }

  @Override
  public void close() throws IOException {
    delegate.disable();
  }

  @Override
  public void accept(ReadOnlyVisitor visitor, CursorStep step) {
    errorHandler.wrapVoidCall(delegate.accept(new ReadOnlyVisitorAdapter(visitor), AccessType.READ_ONLY.value(),
        step.value()));
  }

  @Override
  public void accept(WritableVisitor visitor, CursorStep step) {
    errorHandler.wrapVoidCall(delegate.accept(new WritableVisitorAdapter(visitor), AccessType.READ_WRITE.value(),
        step.value()));
  }

  @Override
  public void setValue(byte[] value, CursorStep step) {
    errorHandler.wrapVoidCall(delegate.set_value(value, step.value()));
  }

  @Override
  public void setValue(String value, CursorStep step) {
    errorHandler.wrapVoidCall(delegate.set_value(value, step.value()));
  }

  @Override
  public void remove() {
    errorHandler.wrapVoidCall(delegate.remove());
  }

  @Override
  public byte[] getKey(CursorStep step) {
    return errorHandler.wrapObjectCall(delegate.get_key(step.value()));
  }

  @Override
  public String getKeyAsString(CursorStep step) {
    return errorHandler.wrapObjectCall(delegate.get_key_str(step.value()));
  }

  @Override
  public byte[] getValue(CursorStep step) {
    return errorHandler.wrapObjectCall(delegate.get_value(step.value()));
  }

  @Override
  public String getValueAsString(CursorStep step) {
    return errorHandler.wrapObjectCall(delegate.get_value_str(step.value()));
  }

  @Override
  public byte[][] getEntry(CursorStep step) {
    return errorHandler.wrapObjectCall(delegate.get(step.value()));
  }

  @Override
  public String[] getEntryAsString(CursorStep step) {
    return errorHandler.wrapObjectCall(delegate.get_str(step.value()));
  }

  @Override
  public void scanForwardFromStart() {
    errorHandler.wrapVoidCall(delegate.jump());
  }

  @Override
  public void scanForwardFromKey(byte[] key) {
    errorHandler.wrapVoidCall(delegate.jump(key));
  }

  @Override
  public void scanForwardFromKey(String key) {
    errorHandler.wrapVoidCall(delegate.jump(key));
  }

  @Override
  public void scanBackwardsFromEnd() {
    errorHandler.wrapVoidCall(delegate.jump_back());
  }

  @Override
  public void scanBackwardsFromKey(byte[] key) {
    errorHandler.wrapVoidCall(delegate.jump_back(key));
  }

  @Override
  public void scanBackwardsFromKey(String key) {
    errorHandler.wrapVoidCall(delegate.jump_back(key));
  }

  @Override
  public void stepForwards() {
    errorHandler.wrapVoidCall(delegate.step());
  }

  @Override
  public void stepBackwards() {
    errorHandler.wrapVoidCall(delegate.step_back());
  }

}
