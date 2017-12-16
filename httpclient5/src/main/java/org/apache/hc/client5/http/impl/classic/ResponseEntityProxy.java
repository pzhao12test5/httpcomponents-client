/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.client5.http.impl.classic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.EofSensorInputStream;
import org.apache.hc.core5.http.io.EofSensorWatcher;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;

/**
 * A wrapper class for {@link HttpEntity} enclosed in a response message.
 *
 * @since 4.3
 */
class ResponseEntityProxy extends HttpEntityWrapper implements EofSensorWatcher {

    private final ExecRuntime execRuntime;

    public static void enchance(final ClassicHttpResponse response, final ExecRuntime execRuntime) {
        final HttpEntity entity = response.getEntity();
        if (entity != null && entity.isStreaming() && execRuntime != null) {
            response.setEntity(new ResponseEntityProxy(entity, execRuntime));
        }
    }

    ResponseEntityProxy(final HttpEntity entity, final ExecRuntime execRuntime) {
        super(entity);
        this.execRuntime = execRuntime;
    }

    private void cleanup() throws IOException {
        if (this.execRuntime != null) {
            if (this.execRuntime.isConnected()) {
                this.execRuntime.disconnect();
            }
            this.execRuntime.discardConnection();
        }
    }

    private void discardConnection() {
        if (this.execRuntime != null) {
            this.execRuntime.discardConnection();
        }
    }

    public void releaseConnection() {
        if (this.execRuntime != null) {
            this.execRuntime.releaseConnection();
        }
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public InputStream getContent() throws IOException {
        return new EofSensorInputStream(super.getContent(), this);
    }

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        try {
            if (outstream != null) {
                super.writeTo(outstream);
            }
            releaseConnection();
        } catch (IOException | RuntimeException ex) {
            discardConnection();
            throw ex;
        } finally {
            cleanup();
        }
    }

    @Override
    public boolean eofDetected(final InputStream wrapped) throws IOException {
        try {
            // there may be some cleanup required, such as
            // reading trailers after the response body:
            if (wrapped != null) {
                wrapped.close();
            }
            releaseConnection();
        } catch (IOException | RuntimeException ex) {
            discardConnection();
            throw ex;
        } finally {
            cleanup();
        }
        return false;
    }

    @Override
    public boolean streamClosed(final InputStream wrapped) throws IOException {
        try {
            final boolean open = execRuntime != null && execRuntime.isConnectionAcquired();
            // this assumes that closing the stream will
            // consume the remainder of the response body:
            try {
                if (wrapped != null) {
                    wrapped.close();
                }
                releaseConnection();
            } catch (final SocketException ex) {
                if (open) {
                    throw ex;
                }
            }
        } catch (IOException | RuntimeException ex) {
            discardConnection();
            throw ex;
        } finally {
            cleanup();
        }
        return false;
    }

    @Override
    public boolean streamAbort(final InputStream wrapped) throws IOException {
        cleanup();
        return false;
    }

}
