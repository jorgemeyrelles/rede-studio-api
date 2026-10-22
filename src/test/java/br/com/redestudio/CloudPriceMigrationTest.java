package br.com.redestudio;

import br.com.redestudio.entities.CloudPriceEntity;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.repositories.CloudPriceRepository;
import br.com.redestudio.runners.CloudPriceMigration;
import com.mongodb.client.MongoClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migration local de preços ({@link CloudPriceMigration}): banco vazio é
 * carregado (só o recorte do Studio, com USD e BRL), banco com dados não é
 * tocado e reexecutar não duplica. Rede trocada por
 * {@link FakeAwsPriceListClient}/{@link FakePtaxClient}; banco próprio,
 * apagado ao fim de cada teste.
 */
@QuarkusTest
@TestProfile(CloudPriceMigrationTest.OwnDatabase.class)
class CloudPriceMigrationTest {

    static final String DATABASE = "rede_cloud_migration_test";

    /** Banco exclusivo: o critério "coleção vazia" não pode depender do que outros testes deixaram. */
    public static class OwnDatabase implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("app.cloud-pricing.database", DATABASE);
        }
    }

    @Inject
    CloudPriceMigration migration;

    @Inject
    CloudPriceRepository repository;

    @Inject
    MongoClient mongoClient;

    @BeforeEach
    @AfterEach
    void dropDatabase() {
        mongoClient.getDatabase(DATABASE).drop();
    }

    @Test
    void bancoVazioRecebeSoORecorteComUsdEBrl() {
        assertEquals(DATABASE, repository.databaseName());
        assertTrue(repository.isEmpty());

        assertEquals(3, migration.migrateIfEmpty());

        List<CloudPriceEntity> items = repository.findByRegion(CloudProvider.AWS, "sa-east-1");
        assertEquals(List.of("aws.data_transfer", "aws.ec2.instance", "aws.vpc.nat_gateway"),
                items.stream().map(CloudPriceEntity::getResourceType).sorted().toList());

        CloudPriceEntity t3 = items.stream().filter(i -> i.getResourceType().equals("aws.ec2.instance")).findFirst().orElseThrow();
        assertEquals("SKU1.JRTCKXETXF.6YS6EN2CT7", t3.getRateCode());
        assertEquals(0, new BigDecimal("0.0168").compareTo(t3.getPrice().getUsd()));
        assertEquals(0, new BigDecimal("0.08538768").compareTo(t3.getPrice().getBrl()));
        assertEquals("South America (Sao Paulo)", t3.getRegionName());
        assertEquals("20261001000000", t3.getSource().getPublicationVersion());
        assertNull(t3.getTierEnd());

        CloudPriceEntity egress = items.stream().filter(i -> i.getResourceType().equals("aws.data_transfer")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("10240").compareTo(egress.getTierEnd()));
    }

    @Test
    void bancoComDadosNaoETocadoEReexecutarNaoDuplica() {
        migration.migrateIfEmpty();
        assertFalse(repository.isEmpty());

        assertEquals(0, migration.migrateIfEmpty());
        assertEquals(3, repository.findByRegion(CloudProvider.AWS, "sa-east-1").size());
    }
}
