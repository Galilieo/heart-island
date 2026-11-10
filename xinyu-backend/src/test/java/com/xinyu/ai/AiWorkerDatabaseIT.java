package com.xinyu.ai;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 实际Hikari／Connector/J及隔离MySQL故障探针；不读取业务配置，不使用真实模型。 */
class AiWorkerDatabaseIT {
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);

    @Test
    void exhaustedPoolReturnsWhileBorrowerStillHoldsOnlyConnection() throws Exception {
        try (HikariDataSource pool = pool(testUrl(), 1); Connection held = pool.getConnection()) {
            long started = System.nanoTime();
            assertThatThrownBy(pool::getConnection).isInstanceOf(SQLException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertThat(elapsed).isBetween(Duration.ofMillis(1500), Duration.ofSeconds(5));
            assertThat(held.isClosed()).isFalse();
            System.out.println("3DE_REAL_DB pool_exhausted_ms=" + elapsed.toMillis());
        }
    }

    @Test
    void unavailableDatabaseConnectionAttemptIsFinite() throws Exception {
        URI target = URI.create(testUrl().substring(5));
        int refusedPort;
        try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            refusedPort = reservation.getLocalPort();
        }
        String refused = "jdbc:mysql://127.0.0.1:" + refusedPort + target.getPath();
        try (HikariDataSource pool = pool(refused, 1)) {
            long started = System.nanoTime();
            assertThatThrownBy(pool::getConnection).isInstanceOf(SQLException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertThat(elapsed).isLessThan(MAX_WAIT);
            System.out.println("3DE_REAL_DB unavailable_ms=" + elapsed.toMillis());
        }
    }

    @Test
    void stalledReadExitsAndEvictsConnection() throws Exception {
        probe("SELECT 1", connection -> connection.createStatement().execute("SELECT 1"));
    }

    @Test
    void stalledCommitReplyExitsWithoutClaimingCommitWasRolledBack() throws Exception {
        probe("COMMIT", Connection::commit);
    }

    @Test
    void stalledRollbackReplyExitsWithoutClaimingTransactionWasCommitted() throws Exception {
        probe("ROLLBACK", Connection::rollback);
    }

    private void probe(String command, SqlCall operation) throws Exception {
        URI target = URI.create(testUrl().substring(5));
        try (ReplyGate proxy = new ReplyGate(target.getHost(), target.getPort());
             HikariDataSource pool = pool("jdbc:mysql://127.0.0.1:" + proxy.port() + target.getPath(), 1)) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Duration> finished = executor.submit(() -> {
                    try (Connection connection = pool.getConnection()) {
                        connection.setAutoCommit(false);
                        proxy.blockReplyTo(command);
                        long started = System.nanoTime();
                        assertThatThrownBy(() -> operation.run(connection)).isInstanceOf(SQLException.class);
                        return Duration.ofNanos(System.nanoTime() - started);
                    }
                });
                if (!proxy.blocked.await(3, TimeUnit.SECONDS)) {
                    finished.get(1, TimeUnit.SECONDS);
                    fail("指定命令没有触发响应屏障");
                }
                Duration elapsed = finished.get(MAX_WAIT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(elapsed).isBetween(Duration.ofSeconds(4), MAX_WAIT);
                assertThat(proxy.blockReplies).isTrue();
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
                System.out.println("3DE_REAL_DB " + command.replace(' ', '_') + "_stalled_ms=" + elapsed.toMillis());
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private static HikariDataSource pool(String url, int maximum) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(System.getenv().getOrDefault("AI_TEST_DB_USER", "root"));
        config.setPassword(System.getenv().getOrDefault("AI_TEST_DB_PASSWORD", ""));
        config.setMaximumPoolSize(maximum);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTimeout(2000);
        config.setValidationTimeout(1000);
        config.addDataSourceProperty("connectTimeout", "2000");
        config.addDataSourceProperty("socketTimeout", "6000");
        config.addDataSourceProperty("sslMode", "DISABLED");
        config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
        return new HikariDataSource(config);
    }

    private static String testUrl() {
        String url = System.getenv("AI_TEST_JDBC_URL");
        assumeTrue(url != null, "需要显式隔离测试库");
        URI target = URI.create(url.substring(5));
        if (!url.startsWith("jdbc:mysql://127.0.0.1:") || target.getQuery() != null
                || target.getUserInfo() != null || target.getFragment() != null
                || !target.getPath().matches("/xinyu_test_[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("故障探针仅允许本机显式隔离测试库");
        }
        return url;
    }

    @FunctionalInterface
    private interface SqlCall {
        void run(Connection connection) throws SQLException;
    }

    /** 转发真实 MySQL 报文，只在指定命令发出后扣住服务端响应；阻塞保持到测试关闭。 */
    private static final class ReplyGate implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService relay = Executors.newCachedThreadPool();
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile String command;
        private volatile boolean blockReplies;

        ReplyGate(String host, int port) throws IOException {
            listener = new ServerSocket(0, 3, InetAddress.getLoopbackAddress());
            relay.submit(() -> {
                while (!closed.get()) {
                    try {
                        Socket downstream = listener.accept();
                        Socket upstream = new Socket(host, port);
                        sockets.add(downstream);
                        sockets.add(upstream);
                        relay.submit(() -> forwardRequests(downstream, upstream));
                        relay.submit(() -> forwardReplies(upstream, downstream));
                    } catch (IOException ending) {
                        return;
                    }
                }
            });
        }

        int port() {
            return listener.getLocalPort();
        }

        void blockReplyTo(String command) {
            this.command = command;
        }

        private void forwardRequests(Socket from, Socket to) {
            try {
                InputStream input = from.getInputStream();
                while (!closed.get()) {
                    byte[] header = input.readNBytes(4);
                    if (header.length != 4) {
                        return;
                    }
                    int length = (header[0] & 255) | ((header[1] & 255) << 8) | ((header[2] & 255) << 16);
                    byte[] body = input.readNBytes(length);
                    if (body.length != length) {
                        return;
                    }
                    if (body.length > 1 && body[0] == 3) {
                        // Connector/J 9.7与MySQL8协商QUERY_ATTRIBUTES后，即使无属性也会先写0、1。
                        int sqlOffset = body.length > 3 && body[1] == 0 && body[2] == 1 ? 3 : 1;
                        String sql = new String(body, sqlOffset, body.length - sqlOffset, StandardCharsets.UTF_8);
                        if (sql.equalsIgnoreCase(command)) {
                            blockReplies = true;
                            blocked.countDown();
                        }
                    }
                    to.getOutputStream().write(header);
                    to.getOutputStream().write(body);
                    to.getOutputStream().flush();
                }
            } catch (IOException ending) {
                // 测试关闭与驱动超时都会关闭连接；不打印报文或原始异常。
            }
        }

        private void forwardReplies(Socket from, Socket to) {
            try {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = from.getInputStream().read(buffer)) >= 0) {
                    if (blockReplies) {
                        continue;
                    }
                    to.getOutputStream().write(buffer, 0, count);
                    to.getOutputStream().flush();
                }
            } catch (IOException ending) {
                // 连接超时或测试关闭。
            }
        }

        @Override
        public void close() throws Exception {
            closed.set(true);
            listener.close();
            for (Socket socket : sockets) {
                socket.close();
            }
            relay.shutdownNow();
            assertThat(relay.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }
}
