//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.http2.client.http;

import org.eclipse.jetty.client.HttpChannel;
import org.eclipse.jetty.client.HttpDestination;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.client.HttpReceiver;
import org.eclipse.jetty.client.HttpSender;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http2.ErrorCode;
import org.eclipse.jetty.http2.IStream;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.frames.ResetFrame;
import org.eclipse.jetty.util.Callback;

public class HttpChannelOverHTTP2 extends HttpChannel
{
    private final HttpConnectionOverHTTP2 connection;
    private final Session session;
    private final HttpSenderOverHTTP2 sender;
    private final HttpReceiverOverHTTP2 receiver;
    private Stream stream;

    public HttpChannelOverHTTP2(HttpDestination destination, HttpConnectionOverHTTP2 connection, Session session)
    {
        super(destination);
        this.connection = connection;
        this.session = session;
        this.sender = new HttpSenderOverHTTP2(this);
        this.receiver = new HttpReceiverOverHTTP2(this);
    }

    protected HttpConnectionOverHTTP2 getHttpConnection()
    {
        return connection;
    }

    public Session getSession()
    {
        return session;
    }

    public Stream.Listener getStreamListener()
    {
        return receiver;
    }

    @Override
    protected HttpSender getHttpSender()
    {
        return sender;
    }

    @Override
    protected HttpReceiver getHttpReceiver()
    {
        return receiver;
    }

    public Stream getStream()
    {
        return stream;
    }

    public void setStream(Stream stream)
    {
        this.stream = stream;
    }

    public boolean isFailed()
    {
        return sender.isFailed() || receiver.isFailed();
    }

    @Override
    public void send(HttpExchange exchange)
    {
        sender.send(exchange);
    }

    @Override
    public void release()
    {
        connection.release(this);
    }

    void onStreamClosed(IStream stream)
    {
        connection.onStreamClosed(stream, this);
    }

    @Override
    public void exchangeTerminated(HttpExchange exchange, Result result)
    {
        super.exchangeTerminated(exchange, result);
        if (result.isSucceeded())
        {
            release();
        }
        else
        {
            Stream stream = getStream();
            if (stream != null)
                stream.reset(new ResetFrame(stream.getId(), ErrorCode.CANCEL_STREAM_ERROR.code), new ReleaseCallback());
            else
                release();
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s[send=%s,recv=%s]",
                super.toString(),
                sender,
                receiver);
    }

    private class ReleaseCallback implements Callback
    {
        @Override
        public void succeeded()
        {
            release();
        }

        @Override
        public void failed(Throwable x)
        {
            if (LOG.isDebugEnabled())
                LOG.debug(x);
            release();
        }

        @Override
        public InvocationType getInvocationType()
        {
            return InvocationType.NON_BLOCKING;
        }
    }
}
