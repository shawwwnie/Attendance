package dev.careflow;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.ArrayList;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;

/** Disposable PostgreSQL for local demonstrations; never packaged in the production JAR. */
public class LocalDemoServer {
  public static void main(String[] args) throws Exception {
    EmbeddedPostgres pg =
        EmbeddedPostgres.builder()
            .setPort(0)
            .setServerConfig("unix_socket_directories", "")
            .start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    pg.close();
                  } catch (Exception ignored) {
                  }
                }));
    var options =
        new ArrayList<>(
            Arrays.asList(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + pg.getJdbcUrl("postgres", "postgres"),
                "--spring.datasource.username=postgres",
                "--spring.datasource.password="));
    options.addAll(Arrays.asList(args));
    SpringApplication.run(CareflowApplication.class, options.toArray(String[]::new));
  }
}
