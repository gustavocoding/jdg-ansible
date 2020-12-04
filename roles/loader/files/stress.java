////usr/bin/env jbang "$0" "$@" ; exit $? # (1)
//DEPS org.openjdk.jmh:jmh-core:1.26
//DEPS org.openjdk.jmh:jmh-generator-annprocess:1.26
//DEPS org.infinispan:infinispan-client-hotrod:9.4.21.Final
//DEPS info.picocli:picocli:4.5.0
//JAVA_OPTIONS -Xmx10g

package org.infinispan;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.infinispan.client.hotrod.ProtocolVersion;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.marshall.UTF8StringMarshaller;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@State(Scope.Benchmark)
@Fork(value = 0, jvmArgs = {"-Xmx20G"})
@Command(name = "Stress", mixinStandardHelpOptions = true, version = "stress 0.1", description = "Simple stress test for JDG.")
public class stress implements Callable<Void> {
   public static final String DEFAULT_PROTOCOL = "2.5";
   public static final String DEFAULT_CACHE = "default";
   public static final String DEFAULT_SERVER = "127.0.0.1:11222";
   public static final String DEFAULT_THREADS = "10";
   public static final String DEFAULT_DURATION_MIN = "1";
   public static final String DEFAULT_WRITE_RATIO = "70";

   @Option(names = "-t", description = "The number of threads.", defaultValue = DEFAULT_THREADS)
   private int threads;

   @Option(names = "-d", description = "The duration in minutes.", defaultValue = DEFAULT_DURATION_MIN)
   private int durationMin;

   @Option(names = "-c", description = "The cache name.", defaultValue = DEFAULT_CACHE)
   @Param(value = DEFAULT_CACHE)
   private String cacheName;

   @Option(names = "-p", description = "HotRod protocol version.", defaultValue = DEFAULT_PROTOCOL)
   @Param(value = DEFAULT_PROTOCOL)
   private String protocol;

   @Option(names = "-s", description = "Server host and port", defaultValue = DEFAULT_SERVER)
   @Param(value = DEFAULT_SERVER)
   private String server;

   @Option(names = "-r", description = "Write percentage", defaultValue = DEFAULT_WRITE_RATIO)
   @Param(value = DEFAULT_WRITE_RATIO)
   private String ratio;

   private static final Random RANDOM = new Random();

   RemoteCache<String, String> cache;
   int size;

   public static void main(String[] args) {
      int exitCode = new CommandLine(new stress()).execute(args);
      System.exit(exitCode);
   }

   @Override
   public Void call() throws Exception {
      System.out.println("Proto :" + protocol);
      Options opt = new OptionsBuilder()
            .include(stress.class.getSimpleName())
            .forks(0)
            .measurementIterations(1)
            .threads(threads)
            .param("protocol", protocol)
            .param("server", server)
            .param("cacheName", cacheName)
            .param("ratio", ratio)
            .measurementTime(new TimeValue(durationMin, TimeUnit.MINUTES))
            .build();
      new Runner(opt).run();
      return null;
   }


   String protocolValue;

   @Setup
   public void setup() {
      ConfigurationBuilder clientBuilder = new ConfigurationBuilder();
      clientBuilder.addServers(server + ";");
      clientBuilder.version(ProtocolVersion.PROTOCOL_VERSION_25);
      clientBuilder.marshaller(new UTF8StringMarshaller());
      RemoteCacheManager rcm = new RemoteCacheManager(clientBuilder.build());
      cache = rcm.getCache(cacheName);
      size = cache.size();
   }

   @Benchmark
   public void loadGenerator(Blackhole ignored) {
      int k = RANDOM.nextInt(size);
      String key = String.valueOf(k);
      String value = cache.get(key);
      cache.put(key, value + "changed");
      if (k < size * Integer.parseInt(ratio) / 100) {
         if (value != null) cache.put(key + k, value);
      } else {
         cache.remove(key);
      }
   }
}


