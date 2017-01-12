/*
 * ToroDB
 * Copyright © 2014 8Kdata Technology (www.8kdata.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.torodb.mongodb.repl;


import com.eightkdata.mongowp.client.wrapper.MongoClientConfiguration;
import com.eightkdata.mongowp.server.api.impl.NameBasedCommandLibrary;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.torodb.core.modules.BundleConfig;
import com.torodb.core.modules.BundleConfigImpl;
import com.torodb.core.supervision.Supervisor;
import com.torodb.core.supervision.SupervisorDecision;
import com.torodb.engine.essential.EssentialModule;
import com.torodb.mongodb.commands.impl.EmptyCommandClassifier;
import com.torodb.mongodb.core.MongoDbCoreBundle;
import com.torodb.mongodb.core.MongoDbCoreConfig;
import com.torodb.mongodb.repl.impl.AlwaysConsistentConsistencyHandler;
import com.torodb.torod.MemoryTorodBundle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.util.regex.Pattern;

public class MongoDbReplBundleTest {

  private BundleConfig generalConfig;
  private MemoryTorodBundle torodBundle;
  private MongoDbCoreBundle coreBundle;

  @Before
  public void setUp() {
    Supervisor supervisor = new Supervisor() {
      @Override
      public SupervisorDecision onError(Object supervised, Throwable error) {
        throw new AssertionError("error on " + supervised, error);
      }
    };
    Injector essentialInjector = Guice.createInjector(
        new EssentialModule(
            () -> true,
            Clock.systemUTC()
        )
    );

    generalConfig = new BundleConfigImpl(essentialInjector, supervisor);
    torodBundle = new MemoryTorodBundle(generalConfig);

    torodBundle.startAsync();
    torodBundle.awaitRunning();

    MongoDbCoreConfig config = new MongoDbCoreConfig(torodBundle,
        new NameBasedCommandLibrary("test", ImmutableMap.of()),
        new EmptyCommandClassifier(), essentialInjector, supervisor);
    coreBundle = new MongoDbCoreBundle(config);

    coreBundle.startAsync();
    coreBundle.awaitRunning();
  }

  @After
  public void tearDown() {
    if (coreBundle != null && coreBundle.isRunning()) {
      coreBundle.stopAsync();
    }
  }

  @Test
  public void testConstruction() {
    //This bundle requires remotes nodes, so it cannot start without them
    //This is why this test only checks that the bundle can be created, but not that it can start
    MongoDbReplBundle replBundle = new MongoDbReplBundle(new MongoDbReplConfigBuilder(generalConfig)
        .setConsistencyHandler(new AlwaysConsistentConsistencyHandler())
        .setMongoClientConfiguration(createMongoClientConfiguration())
        .setReplSetName("replTest")
        .setReplicationFilters(createReplicationFilters())
        .setCoreBundle(coreBundle)
        .build()
    );
    assert !replBundle.isRunning();
  }

  private MongoClientConfiguration createMongoClientConfiguration() {
    return MongoClientConfiguration.unsecure(HostAndPort.fromParts("localhost", 27017));
  }

  private ReplicationFilters createReplicationFilters() {
    return new ReplicationFilters(
        ImmutableMap.<Pattern, ImmutableMap<Pattern, ImmutableList<ReplicationFilters.IndexPattern>>>of(),
        ImmutableMap.<Pattern, ImmutableMap<Pattern, ImmutableList<ReplicationFilters.IndexPattern>>>of());
  }
}