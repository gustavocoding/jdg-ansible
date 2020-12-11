////usr/bin/env jbang "$0" "$@" ; exit $? # (1)
//DEPS org.openjdk.jmh:jmh-core:1.26
//DEPS org.openjdk.jmh:jmh-generator-annprocess:1.26
//DEPS org.infinispan:infinispan-client-hotrod:9.4.21.Final
//DEPS info.picocli:picocli:4.5.0
//SOURCES loader.java
//FILES words.txt
//JAVA_OPTIONS -Xmx10g

package org.infinispan;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.infinispan.client.hotrod.ProtocolVersion;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.marshall.UTF8StringMarshaller;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
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
@Fork(value = 1, jvmArgs = {"-Xmx20G"})
@Command(name = "Stress", mixinStandardHelpOptions = true, version = "stress 0.1", description = "Simple stress test for JDG.")
@SuppressWarnings("unused")
public class stress implements Callable<Void> {
   public static final String DEFAULT_PROTOCOL = "2.5";
   public static final String DEFAULT_CACHE = "default";
   public static final String DEFAULT_SERVER = "127.0.0.1:11222";
   public static final String DEFAULT_THREADS = "10";
   public static final String DEFAULT_DURATION_MIN = "1";

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

   @Option(names = "-w", description = "Write percentage", required = true)
   @Param(value = "0")
   private String writePercent;

   @Option(names = "-g", description = "Get percentage", required = true)
   @Param(value = "0")
   private String getPercent;

   @Option(names = "-r", description = "Remove percentage", required = true)
   @Param(value = "0")
   private String removePercent;

   private static final Random RANDOM = new Random();

   RemoteCache<String, String> cache;
   Helper helper;

   public static void main(String[] args) {
      int exitCode = new CommandLine(new stress()).execute(args);
      System.exit(exitCode);
   }

   @Override
   public Void call() throws Exception {
      Options opt = new OptionsBuilder()
            .include(stress.class.getSimpleName())
            .measurementIterations(1)
            .threads(threads)
            .warmupIterations(0)
            .param("protocol", protocol)
            .param("server", server)
            .param("cacheName", cacheName)
            .param("writePercent", writePercent)
            .param("getPercent", getPercent)
            .param("removePercent", removePercent)
            .measurementTime(new TimeValue(durationMin, TimeUnit.MINUTES))
            .output(String.format("result-%d.txt", System.currentTimeMillis()))
            .build();
      new Runner(opt).run();
      return null;
   }

   @Setup
   public void setup() {
      ConfigurationBuilder clientBuilder = new ConfigurationBuilder();
      clientBuilder.addServers(server + ";");
      clientBuilder.version(ProtocolVersion.PROTOCOL_VERSION_25);
      clientBuilder.marshaller(new UTF8StringMarshaller());
      clientBuilder.forceReturnValues(true);
      RemoteCacheManager rcm = new RemoteCacheManager(clientBuilder.build());
      cache = rcm.getCache(cacheName);
      helper = new Helper(writePercent, getPercent, removePercent, cache.size());
   }

   @Benchmark
   @BenchmarkMode({Mode.SampleTime})
   public void loadGenerator(Blackhole blackhole) {
      switch (helper.generateOp()) {
         case PUT:
            String newId = helper.generateKeyForInserting();
            blackhole.consume(cache.put(newId, org.infinispan.loader.randomPhrase(10)));
            break;
         case REMOVE:
            String keyToRemove = helper.generateKeyForDeleting();
            blackhole.consume(cache.remove(keyToRemove));
            break;
         default:
            String keyToRead = helper.generateKeyForReading();
            blackhole.consume(cache.get(keyToRead));
            break;
      }
   }

   static final class Helper {
      enum Operation {PUT, GET, REMOVE}

      private final Random random = new Random(0);
      private final int writePercent;
      private final int readPercent;
      private final int maxGetIndex;
      private final AtomicInteger getCounter = new AtomicInteger(0);
      private final AtomicInteger minDeleteIndex;
      private final AtomicInteger minPutIndex;

      public Helper(String writePct, String getPct, String removePct, int size) {
         this.writePercent = Integer.parseInt(writePct);
         this.readPercent = Integer.parseInt(getPct);
         int removePercent = Integer.parseInt(removePct);

         if (writePercent + readPercent + removePercent != 100) throw new RuntimeException("Invalid Percentages");

         this.maxGetIndex = size / 2;
         this.minDeleteIndex = new AtomicInteger(maxGetIndex + 1);
         this.minPutIndex = new AtomicInteger(size);
      }

      String generateKeyForReading() {
         return String.valueOf(getCounter.incrementAndGet() % maxGetIndex);
      }

      String generateKeyForDeleting() {
         return String.valueOf(minDeleteIndex.incrementAndGet());
      }

      String generateKeyForInserting() {
         return String.valueOf(minPutIndex.incrementAndGet());
      }

      Operation generateOp() {
         int i = random.nextInt(100);
         if (i < writePercent) return Operation.PUT;
         if (i < readPercent + writePercent) return Operation.GET;
         return Operation.REMOVE;
      }
   }
}


