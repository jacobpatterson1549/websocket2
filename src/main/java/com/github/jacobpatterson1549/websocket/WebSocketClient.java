package com.github.jacobpatterson1549.websocket;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketClient
        extends Endpoint
        implements MessageHandler.Whole<String> {

    private final Logger logger;
    private final Object lock;
    private boolean isSending;
    private String message;
    private CountDownLatch receiveLatch;

    public WebSocketClient() {
        this.logger = Logger.getGlobal();
        this.lock = new Object();
        this.isSending = true;
    }

    public static void main(String[] args) throws Exception {
        WebSocketContainer webSocketContainer
                = ContainerProvider.getWebSocketContainer();
        URI serverURI = new URI("wss://echo.WebSocket.org");
        WebSocketClient webSocketClient = new WebSocketClient();
        ClientEndpointConfig clientEndpointConfig =
                ClientEndpointConfig.Builder.create()
                        .build();
        try (Session session =
                     webSocketContainer.connectToServer(webSocketClient,
                             clientEndpointConfig,
                             serverURI)) {
            RemoteEndpoint.Basic messageProducer = session.getBasicRemote();
            session.addMessageHandler(webSocketClient);

            String outMessage = "Hello, World!";
            webSocketClient.send(messageProducer, outMessage);
            String inMessage = webSocketClient.receive();
            System.out.println(inMessage);
        }
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        logger.info("Session opened");
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        logger.info("Session closed");
    }

    @Override
    public void onError(Session session, Throwable thr) {
        logger.log(Level.SEVERE, "WebSocket error", thr);
        try {
            session.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void send(RemoteEndpoint.Basic messageProducer, String message)
            throws IOException {
        logger.info("sending: " + message);
        synchronized (lock) {
            if (!isSending) {
                throw new RuntimeException("did not expect to receive message");
            }
            isSending = false;
        }
        receiveLatch = new CountDownLatch(1);
        messageProducer.sendText(message);
    }

    public String receive() {
        logger.info("requested receive...");
        synchronized (lock) {
            if (isSending) {
                throw new RuntimeException("did not expect to receive message");
            }
        }
        try {
            boolean timedOut = !receiveLatch.await(60, TimeUnit.SECONDS);
            if (timedOut) {
                throw new RuntimeException("Timeout out waiting to receive");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        isSending = true;
        return message;
    }

    @Override
    public void onMessage(String message) {
        logger.info("receiving " + message);
        synchronized (lock) {
            if (isSending) {
                throw new RuntimeException("did not expect to receive message");
            }
        }
        this.message = message;
        receiveLatch.countDown();
    }
}
