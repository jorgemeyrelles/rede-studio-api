package br.com.redestudio.components.pricing;

import br.com.redestudio.components.pricing.AwsPriceClassifier.Classification;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recorte da Price List (porte do antigo {@code fetch_aws.py}): as regras que
 * já causaram defeito ou que dependem de detalhe do CSV da AWS. A paridade
 * completa com a carga em Python (331 itens de sa-east-1, 92 globais) foi
 * conferida contra a AWS real na migração para Java.
 */
class AwsPriceClassifierTest {

    private static Map<String, String> row(String... keyValues) {
        Map<String, String> row = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put(keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private static Map<String, String> ec2Instance(String type, String os) {
        return row("Product Family", "Compute Instance", "Instance Type", type, "Operating System", os,
                "Tenancy", "Shared", "Pre Installed S/W", "NA", "CapacityStatus", "Used",
                "License Model", "No License required", "MarketOption", "OnDemand",
                "vCPU", "2", "Memory", "1,952 GiB", "Processor Architecture", "64-bit",
                "Network Performance", "Up to 5 Gigabit", "usageType", "SAE1-BoxUsage:" + type);
    }

    @Test
    void ec2LinuxSharedOnDemandEntraComAtributos() {
        Classification c = AwsPriceClassifier.classify("AmazonEC2", ec2Instance("t3.micro", "Linux")).orElseThrow();
        assertEquals("aws.ec2.instance", c.resourceType());
        assertEquals("t3", c.attributes().get("instanceFamily"));
        assertEquals(2, c.attributes().get("vcpu"));
        assertEquals(1952.0, c.attributes().get("memoryGiB"));
    }

    @Test
    void ec2ForaDoRecorteNaoEntra() {
        assertTrue(AwsPriceClassifier.classify("AmazonEC2", ec2Instance("t3.micro", "Windows")).isEmpty());
        assertTrue(AwsPriceClassifier.classify("AmazonEC2", ec2Instance("x2idn.large", "Linux")).isEmpty());
    }

    @Test
    void transferenciaSoComFamiliaDataTransfer() {
        Map<String, String> out = row("Product Family", "Data Transfer", "usageType", "SAE1-DataTransfer-Out-Bytes");
        assertEquals("internet_out", AwsPriceClassifier.classify("AmazonEC2", out).orElseThrow().attributes().get("kind"));
        out.put("Product Family", "Other");
        assertTrue(AwsPriceClassifier.classify("AmazonEC2", out).isEmpty());
    }

    @Test
    void usageIsAceitaComESemPrefixoDeRegiao() {
        assertTrue(AwsPriceClassifier.usageIs(row("usageType", "SAE1-Requests-Tier1"), "Requests-Tier1"));
        assertTrue(AwsPriceClassifier.usageIs(row("usageType", "Requests-Tier1"), "Requests-Tier1"));
        assertTrue(AwsPriceClassifier.usageIs(row("usageType", "EUC1-Requests-Tier1"), "Requests-Tier1"));
        assertFalse(AwsPriceClassifier.usageIs(row("usageType", "SAE1-Requests-FIFO-Tier1"), "Requests-Tier1"));
    }

    @Test
    void unusedBoxDoRdsEDoAuroraFicaDeFora() {
        Map<String, String> rds = row("Product Family", "Database Instance", "Database Engine", "PostgreSQL",
                "Deployment Option", "Single-AZ", "Instance Type", "db.t4g.micro", "vCPU", "2", "Memory", "1 GiB",
                "usageType", "SAE1-InstanceUsage:db.t4g.micro");
        assertEquals("aws.rds.instance", AwsPriceClassifier.classify("AmazonRDS", rds).orElseThrow().resourceType());
        rds.put("usageType", "SAE1-UnusedBox:db.t4g.micro");
        assertTrue(AwsPriceClassifier.classify("AmazonRDS", rds).isEmpty());

        Map<String, String> aurora = row("Product Family", "Database Instance", "Database Engine", "Aurora PostgreSQL",
                "Instance Type", "db.r7g.large", "vCPU", "2", "Memory", "16 GiB",
                "usageType", "SAE1-InstanceUsageIOOptimized:db.r7g.large");
        Classification c = AwsPriceClassifier.classify("AmazonRDS", aurora).orElseThrow();
        assertEquals("aws.aurora.instance", c.resourceType());
        assertEquals("io-optimized", c.attributes().get("storageMode"));
        aurora.put("usageType", "SAE1-UnusedBox:db.r7g.large");
        assertTrue(AwsPriceClassifier.classify("AmazonRDS", aurora).isEmpty());
    }

    @Test
    void franquiasGlobaisVemAntesDaCobranca() {
        assertEquals("free_requests", kind("AWSLambda", "Global-Request"));
        assertEquals("requests", kind("AWSLambda", "SAE1-Request"));
        assertEquals("free_requests", kind("awskms", "Global-KMS-Requests"));
        assertEquals("key", kind("awskms", "sa-east-1-KMS-Keys"));
        assertEquals("free_requests", kind("AWSQueueService", "Global-Requests"));
    }

    @Test
    void cloudFrontPorGeografia() {
        Classification c = AwsPriceClassifier.classify("AmazonCloudFront", row("usageType", "SA-DataTransfer-Out-Bytes")).orElseThrow();
        assertEquals("data_out", c.attributes().get("kind"));
        assertEquals("SA", c.attributes().get("geo"));
        assertTrue(AwsPriceClassifier.classify("AmazonCloudFront", row("usageType", "XX-DataTransfer-Out-Bytes")).isEmpty());
    }

    @Test
    void ofertaDesconhecidaNaoEntra() {
        assertTrue(AwsPriceClassifier.classify("AmazonLightsail", row("usageType", "x")).isEmpty());
    }

    private static String kind(String offer, String usageType) {
        Optional<Classification> c = AwsPriceClassifier.classify(offer, row("usageType", usageType));
        return c.map(x -> (String) x.attributes().get("kind")).orElse(null);
    }
}
