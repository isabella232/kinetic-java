/**
 * Copyright (C) 2014 Seagate Technology.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package com.seagate.kinetic.client.internal;

import java.io.IOException;
import java.security.Key;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.spec.SecretKeySpec;

import kinetic.client.CallbackHandler;
import kinetic.client.ClientConfiguration;
import kinetic.client.KineticClient;
import kinetic.client.KineticException;

import com.google.protobuf.ByteString;
import com.seagate.kinetic.client.io.IoHandler;
import com.seagate.kinetic.common.lib.Hmac;
import com.seagate.kinetic.common.lib.Hmac.HmacException;
import com.seagate.kinetic.common.lib.KineticMessage;
import com.seagate.kinetic.proto.Kinetic.Message;
import com.seagate.kinetic.proto.Kinetic.Message.Builder;
import com.seagate.kinetic.proto.Kinetic.Message.Header;
import com.seagate.kinetic.proto.Kinetic.Message.MessageType;
import com.seagate.kinetic.proto.Kinetic.Message.Range;


/**
 * Perform request response operation synchronously or asynchronously on behalf
 * of a Kinetic client.
 *
 * @see KineticClient
 * @see DefaultKineticClient
 *
 * @author James Hughes
 * @author Chiaming Yang
 */
public class ClientProxy {

    private final static Logger logger = Logger.getLogger(ClientProxy.class
            .getName());

    // client configuration
    private ClientConfiguration config = null;

    // client io handler
    private IoHandler iohandler = null;

    // user id
    private long user = 1;

    // connection id
    private long connectionID = 1234;

    // sequence
    private long sequence = 1;

    // cluster version
    private long clusterVersion = 43;

    // key associated with this client instance
    private Key myKey = null;

    // hmac key map
    private final Map<Long, Key> hmacKeyMap = new HashMap<Long, Key>();

    // use protocol v2
    private boolean useV2Protocol = true;

    /**
     * Construct a new instance of client proxy
     *
     * @param config
     *            client configuration for the current instance
     * @throws KineticException
     *             if any internal error occurred.
     */
    public ClientProxy(ClientConfiguration config) throws KineticException {

        // client config
        this.config = config;

        // set to true if v2 protocol is used
        this.useV2Protocol = true;

        // get user principal from client config
        user = config.getUserId();

        // build aclmap
        this.buildHmacKeyMap();

        // get cluster version from client config
        this.clusterVersion = config.getClusterVersion();

        // connection id
        this.connectionID = config.getConnectionId();

        // io handler
        this.iohandler = new IoHandler(this);
    }

    /**
     * Get client configuration instance for this client instance.
     *
     * @return client configuration instance for this client instance.
     */
    public ClientConfiguration getConfiguration() {
        return this.config;
    }

    /**
     * build client acl instance.
     *
     * @return acl instance.
     */
    private void buildHmacKeyMap() {

        Key key = new SecretKeySpec(ByteString.copyFromUtf8(config.getKey())
                .toByteArray(), "HmacSHA1");

        hmacKeyMap.put(config.getUserId(), key);

        this.myKey = key;
    }

    /*
     * KeyRange is a class the defines a range of keys.
     */
    class KeyRange {

        // start key and end key
        private ByteString startKey, endKey;

        // start key, end key, reverse boolean flags
        private boolean startKeyInclusive, endKeyInclusive, reverse;

        // max returned keys
        private int maxReturn;

        /**
         * Constructor for a new instance of key range.
         *
         * @param startKey
         *            the start key of key range
         * @param startKeyInclusive
         *            is start key inclusive
         * @param endKey
         *            end key of key range
         * @param endKeyInclusive
         *            is end key inclusive
         * @param maxReturned
         *            max allowed return keys.
         * @param reverse
         *            true if op is performed in reverse order
         */
        public KeyRange(ByteString startKey, boolean startKeyInclusive,
                ByteString endKey, boolean endKeyInclusive, int maxReturned,
                boolean reverse) {
            setStartKey(startKey);
            setStartKeyInclusive(startKeyInclusive);
            setEndKey(endKey);
            setEndKeyInclusive(endKeyInclusive);
            setMaxReturned(maxReturned);
            setReverse(reverse);
        }

        public ByteString getStartKey() {
            return startKey;
        }

        public void setStartKey(ByteString k1) {
            this.startKey = k1;
        }

        public ByteString getEndKey() {
            return endKey;
        }

        public void setEndKey(ByteString k2) {
            this.endKey = k2;
        }

        public boolean isStartKeyInclusive() {
            return startKeyInclusive;
        }

        public void setStartKeyInclusive(boolean i1) {
            this.startKeyInclusive = i1;
        }

        public boolean isEndKeyInclusive() {
            return endKeyInclusive;
        }

        public void setEndKeyInclusive(boolean i2) {
            this.endKeyInclusive = i2;
        }

        public int getMaxReturned() {
            return maxReturn;
        }

        public void setMaxReturned(int n) {
            this.maxReturn = n;
        }

        public boolean isReverse() {
            return reverse;
        }

        public void setReverse(boolean reverse) {
            this.reverse = reverse;
        }
    }

    /**
     * Returns a list of keys based on the key range specification.
     *
     * @param range
     *            specify the range of keys to be returned. This does not return
     *            the values.
     *
     * @return an array of keys in db that matched the specified range.
     *
     * @throws KineticException
     *             if any internal error occurred.
     *
     * @see KineticClient#getKeyRange(byte[], boolean, byte[], boolean, int)
     * @see KeyRange
     */
    List<ByteString> getKeyRange(KeyRange range) throws KineticException {
        
            // perform key range op
            KineticMessage resp = doRange(range);

            // return list of matched keys.
            return resp.getMessage().getCommand().getBody().getRange()
                    .getKeyList();
    }

    /**
     * Perform range operation based on the specified key range specification.
     *
     * @param keyRange
     *            key range specification to be performed.
     * @return the response message from the range operation.
     *
     * @throws LCException
     *             if any internal error occurred.
     */
    KineticMessage doRange(KeyRange keyRange) throws KineticException {

        //request message
        KineticMessage request = null;
        // response message
        KineticMessage respond = null;
        
        try {
            // request message
            request = MessageFactory
                    .createKineticMessageWithBuilder();
            Message.Builder msg = (Builder) request.getMessage();

            // set message type
            msg.getCommandBuilder().getHeaderBuilder()
            .setMessageType(MessageType.GETKEYRANGE);

            // get range builder
            Range.Builder op = msg.getCommandBuilder().getBodyBuilder()
                    .getRangeBuilder();

            // set parameters for the op
            op.setStartKey(keyRange.getStartKey());
            op.setEndKey(keyRange.getEndKey());
            op.setStartKeyInclusive(keyRange.isStartKeyInclusive());
            op.setEndKeyInclusive(keyRange.isEndKeyInclusive());
            op.setMaxReturned(keyRange.getMaxReturned());
            op.setReverse(keyRange.isReverse());

            // send request
            respond = request(request);
            
            MessageFactory.checkReply(request, respond);

            // return response
            return respond;
        } catch (KineticException ke) {
            //re-throw ke
            throw ke;
        } catch (Exception e) {
            //make a new kinetic exception
            KineticException ke = new KineticException (e);
            ke.setRequestMessage(request);
            ke.setResponseMessage(respond);
            //throw ke
            throw ke;
        }
    }

    public class LCException extends Exception {
        private static final long serialVersionUID = -6118533510243882800L;

        LCException(String s) {
            super(s);
        }
    }

    /**
     * Utility to throw internal LCException.
     *
     * @param exceptionMessage
     *            the message for the exception.
     *
     * @throws LCException
     *             the exception type to be thrown.
     */
    private void throwLcException(String exceptionMessage) throws LCException {
        throw new LCException(exceptionMessage);
    }
    
    /**
     * Send a kinetic request message to drive/simulator.
     * 
     * @param krequest the request message
     * @return response the response message 
     * @throws KineticException if the command operation failed.
     * 
     * @see kinetic.client.VersionMismatchException
     * @see kinetic.client.ClusterVersionFailureException
     */
    KineticMessage request(KineticMessage krequest) throws KineticException {
        
        KineticMessage kresponse = null;
        
        try {
            kresponse = this.doRequest(krequest);
            
            //check status code
            MessageFactory.checkReply(krequest, kresponse);
            
        } catch (KineticException ke) {
            ke.setRequestMessage(krequest);
            ke.setResponseMessage(kresponse);
            throw ke;
        } catch (Exception e) {
            throwKineticException (e, krequest, kresponse);
        }
        
        return kresponse;
    }
    
    private void throwKineticException(Exception e, KineticMessage request,
            KineticMessage response) throws KineticException {

        //new instance
        KineticException ke = new KineticException (e);
        
        //set request message
        ke.setRequestMessage(request);
        //set response message
        ke.setResponseMessage(response);
        
        throw ke;
    }

    /**
     * Send the specified request message synchronously to the Kinetic service.
     *
     * @param message
     *            the request message from the client.
     *
     * @return the response message from the service.
     *
     * @throws LCException
     *             if any errors occur.
     *
     * @see #requestAsync(com.seagate.kinetic.proto.Kinetic.Message.Builder,
     *      CallbackHandler)
     */
    KineticMessage doRequest(KineticMessage im) throws LCException {
        KineticMessage in = null;
        try {

            finalizeHeader(im);

            in = this.iohandler.getMessageHandler().write(im);

            // check if we do received a response
            if (in == null) {
                throwLcException("Timeout - unable to receive response message within " + config.getRequestTimeoutMillis() + " ms");
            }

            // hmac check
            if (!Hmac.check(in, this.myKey)) {
                throwLcException("Hmac failed compare");
            }

        } catch (LCException lce) {
            // re-throw
            throw lce;
        } catch (HmacException e) {
            throwLcException("Hmac failed compute");
        } catch (java.net.SocketTimeoutException e) {
            throwLcException("Socket Timeout");
        } catch (IOException e1) {
            throwLcException("IO error");
        } catch (InterruptedException ite) {
            throwLcException(ite.getMessage());
        }

        return in;
    }

    /**
     *
     * Send the specified request message asynchronously to the Kinetic service.
     *
     * @param message
     *            the request message to be sent asynchronously to the Kinetic
     *            service.
     *
     * @param handler
     *            the callback handler for the asynchronous request.
     *
     * @throws KineticException
     *             if any internal error occur.
     *
     * @see CallbackHandler
     * @see #request(com.seagate.kinetic.proto.Kinetic.Message.Builder)
     */
    <T> void requestAsync(KineticMessage im, CallbackHandler<T> handler)
            throws KineticException {

        try {

            // Message.Builder message = (Builder) im.getMessage();

            // finalize and fill the required header fields for the message
            finalizeHeader(im);

            // get request message to send
            // Message request = message.build();

            // create context message for the async operation
            CallbackContext<T> context = new CallbackContext<T>(handler);

            // set request message to the context so we can get it when response
            // is received
            context.setRequestMessage(im);

            // send the async request message
            this.iohandler.getMessageHandler().writeAsync(im, context);

        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw new KineticException(e.getMessage());
        }
    }

    /**
     * Check hmac based on the specified message.
     *
     * @param message
     *            the protocol buffer message from which hmac value is
     *            validated.
     *
     * @return true if hmac is validate. Otherwise, return false.
     */
    public boolean checkHmac(KineticMessage message) {
        boolean flag = false;

        try {
            Hmac.check(message, this.myKey);
            flag = true;
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }

        return flag;
    }

    /**
     * Filled in required header fields for the request message.
     *
     * @param message
     *            the request protocol buffer message.
     */
    private void finalizeHeader(KineticMessage im) {

        Message.Builder message = (Builder) im.getMessage();

        // get header builder
        Header.Builder header = message.getCommandBuilder().getHeaderBuilder();

        // set cluster version
        header.setClusterVersion(clusterVersion);

        // set user id
        // header.setUser(user);
        header.setIdentity(user);

        // set connection id.
        header.setConnectionID(connectionID);

        // set sequence number.
        header.setSequence(getNextSequence());

        /**
         * calculate and set tag value for the message
         */
        if (header.getMessageType() == MessageType.PUT) {
            if (message.getCommandBuilder().getBodyBuilder().getKeyValueBuilder()
                    .hasTag() == false) {
                if (this.useV2Protocol) {
                    // calculate value Hmac
                    ByteString tag = Hmac.calcTag(im, this.myKey);
                    // set tag
                    message.getCommandBuilder().getBodyBuilder().getKeyValueBuilder()
                    .setTag(tag);
                }
            }
        }

        /**
         * calculate and set hmac value for this message
         */
        try {
            ByteString hmac = Hmac.calc(im, this.myKey);
            message.setHmac(hmac);
        } catch (HmacException e) {

            e.printStackTrace();
        }
    }

    /**
     * Get next sequence number for this connection (client instance).
     *
     * @return next unique number for this client instance.
     */
    private synchronized long getNextSequence() {
        return sequence++;
    }

    /**
     * close io handler and release associated resources.
     */
    void close() {

        if (this.iohandler != null) {
            iohandler.close();
        }

    }

}
