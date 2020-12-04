////usr/bin/env jbang "$0" "$@" ; exit $? # (1)
//DEPS org.openjdk.jmh:jmh-core:1.26
//DEPS org.openjdk.jmh:jmh-generator-annprocess:1.26
//DEPS org.infinispan:infinispan-client-hotrod:9.4.21.Final
//JAVA_OPTIONS -Xmx10g

package org.infinispan;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.marshall.UTF8StringMarshaller;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
//@Warmup(iterations = 3)
//@Measurement(iterations = 8)
public class stress {

   RemoteCache<String, String> cache;
   int size;
   Random r = new Random();

   public static void main(String[] args) throws RunnerException {
      Options opt = new OptionsBuilder()
            .include(stress.class.getSimpleName())
            .forks(1)
            .build();

      new Runner(opt).run();
   }

   @Setup
   public void setup() {
      ConfigurationBuilder clientBuilder = new ConfigurationBuilder();
      clientBuilder.addServer().host("172.17.0.2").port(11222);
      clientBuilder.protocolVersion("2.5");
      clientBuilder.marshaller(new UTF8StringMarshaller());
      RemoteCacheManager rcm = new RemoteCacheManager(clientBuilder.build());
      cache = rcm.getCache("default");
      size = cache.size();
   }

   @Benchmark
   @GroupThreads(16)
   @Measurement(iterations = 1, time = 5, timeUnit = TimeUnit.MINUTES)
   public void loadGenerator(Blackhole bh) {
      int k = r.nextInt(size);
      String key = String.valueOf(k);
      String value = cache.get(key);
      cache.put(key, value + "changed");
      if (k < size * 0.70) {
         cache.put(key + k, value);
      } else {
         cache.remove(key);
      }
   }


}


