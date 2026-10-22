package br.com.redestudio.components.pricing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recorte da AWS Price List que o Studio Cloud usa: para cada linha de uma
 * oferta ({@code AmazonEC2}, {@code AmazonRDS}...), diz se ela entra e com
 * qual {@code resourceType} e atributos — o que o motor de custo do front
 * procura ({@code aws.ec2.instance}, {@code aws.vpc.nat_gateway}...).
 *
 * <p>Função pura sobre o mapa coluna → valor do CSV; sem rede nem banco.
 */
public final class AwsPriceClassifier {

    /** Linha aceita: chave do catálogo do Studio e atributos (sem valores nulos). */
    public record Classification(String resourceType, Map<String, Object> attributes) {
    }

    static final List<String> EC2_FAMILIES = List.of("t3.", "t4g.", "m6i.", "m7g.", "c6i.", "r6i.");
    static final List<String> RDS_CLASSES = List.of("db.t4g.", "db.m6g.");
    static final List<String> RDS_ENGINES = List.of("PostgreSQL", "MySQL");
    static final List<String> RDS_DEPLOYMENTS = List.of("Single-AZ", "Multi-AZ");
    static final List<String> AURORA_CLASSES = List.of("db.t4g.", "db.r7g.", "db.r8g.");
    static final List<String> AURORA_ENGINES = List.of("Aurora PostgreSQL", "Aurora MySQL");
    static final List<String> CACHE_NODES = List.of("cache.t4g.", "cache.m7g.", "cache.r7g.");
    static final List<String> CACHE_ENGINES = List.of("Valkey", "Redis");
    /** Geografias de borda do CloudFront (prefixo do usageType). */
    static final List<String> CLOUDFRONT_GEOS = List.of("SA", "US", "CA", "EU", "JP", "AP", "AU", "IN", "ZA", "ME");

    private static final Pattern MEMORY = Pattern.compile("([\\d.,]+)\\s*GiB");

    private static final Map<String, Function<Map<String, String>, Classification>> CLASSIFIERS = Map.ofEntries(
            Map.entry("AmazonEC2", AwsPriceClassifier::ec2),
            Map.entry("AmazonVPC", AwsPriceClassifier::vpc),
            Map.entry("AWSELB", AwsPriceClassifier::elb),
            Map.entry("AmazonRDS", AwsPriceClassifier::rds),
            Map.entry("AmazonS3", AwsPriceClassifier::s3),
            Map.entry("AmazonCloudFront", AwsPriceClassifier::cloudFront),
            Map.entry("AmazonRoute53", AwsPriceClassifier::route53),
            Map.entry("awswaf", AwsPriceClassifier::waf),
            Map.entry("AWSShield", AwsPriceClassifier::shield),
            Map.entry("awskms", AwsPriceClassifier::kms),
            Map.entry("AWSSecretsManager", AwsPriceClassifier::secrets),
            Map.entry("AWSSystemsManager", AwsPriceClassifier::ssm),
            Map.entry("AmazonCognito", AwsPriceClassifier::cognito),
            Map.entry("AWSLambda", AwsPriceClassifier::lambda),
            Map.entry("AmazonApiGateway", AwsPriceClassifier::apiGateway),
            Map.entry("AmazonECS", AwsPriceClassifier::ecs),
            Map.entry("AmazonEKS", AwsPriceClassifier::eks),
            Map.entry("AmazonECR", AwsPriceClassifier::ecr),
            Map.entry("AmazonElastiCache", AwsPriceClassifier::elastiCache),
            Map.entry("AmazonDynamoDB", AwsPriceClassifier::dynamoDb),
            Map.entry("AWSQueueService", AwsPriceClassifier::sqs),
            Map.entry("AmazonSNS", AwsPriceClassifier::sns),
            Map.entry("AmazonEFS", AwsPriceClassifier::efs),
            Map.entry("AmazonCloudWatch", AwsPriceClassifier::cloudWatch),
            Map.entry("AWSCloudTrail", AwsPriceClassifier::cloudTrail),
            Map.entry("AWSBackup", AwsPriceClassifier::backup),
            Map.entry("AmazonGuardDuty", AwsPriceClassifier::guardDuty),
            Map.entry("AWSSecurityHub", AwsPriceClassifier::securityHub),
            Map.entry("AWSConfig", AwsPriceClassifier::config));

    private AwsPriceClassifier() {
    }

    /** Ofertas que o classificador conhece. */
    public static boolean supports(String offer) {
        return CLASSIFIERS.containsKey(offer);
    }

    public static Optional<Classification> classify(String offer, Map<String, String> row) {
        Function<Map<String, String>, Classification> classifier = CLASSIFIERS.get(offer);
        return classifier == null ? Optional.empty() : Optional.ofNullable(classifier.apply(row));
    }

    // -----------------------------------------------------------------------
    //  Auxiliares
    // -----------------------------------------------------------------------

    static String col(Map<String, String> row, String name) {
        String value = row.get(name);
        return value == null ? "" : value;
    }

    /** {@code SAE1-NatGateway-Hours} → {@code NatGateway-Hours} (tira o prefixo de Região). */
    static String usageSuffix(Map<String, String> row) {
        String usage = col(row, "usageType");
        int dash = usage.indexOf('-');
        return dash >= 0 ? usage.substring(dash + 1) : usage;
    }

    /** usageType é {@code name}, com ou sem o prefixo de Região ({@code SAE1-}, {@code EU-}; us-east-1 não tem). */
    static boolean usageIs(Map<String, String> row, String name) {
        String usage = col(row, "usageType");
        return usage.equals(name) || usage.matches("[A-Z]{2,4}\\d?-" + Pattern.quote(name));
    }

    static Double memoryGib(String value) {
        Matcher matcher = MEMORY.matcher(value == null ? "" : value);
        return matcher.lookingAt() ? Double.valueOf(matcher.group(1).replace(",", "")) : null;
    }

    static Integer asInt(String value) {
        return value != null && !value.isEmpty() && value.chars().allMatch(Character::isDigit)
                ? Integer.valueOf(value)
                : null;
    }

    private static boolean startsWithAny(String value, List<String> prefixes) {
        return prefixes.stream().anyMatch(value::startsWith);
    }

    /** Atributos na ordem dada, sem os valores nulos. */
    private static Classification of(String resourceType, Object... keyValues) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                attributes.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return new Classification(resourceType, attributes);
    }

    private static Classification kind(String resourceType, String kind) {
        return kind == null ? null : of(resourceType, "kind", kind);
    }

    /** Primeiro {@code usageType} da tabela (com ou sem prefixo de Região) → {@code kind}. */
    private static Classification firstUsage(Map<String, String> row, String resourceType, String... namesAndKinds) {
        for (int i = 0; i < namesAndKinds.length; i += 2) {
            if (usageIs(row, namesAndKinds[i])) {
                return of(resourceType, "kind", namesAndKinds[i + 1]);
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  Regras por oferta
    // -----------------------------------------------------------------------

    static Classification ec2(Map<String, String> row) {
        String family = col(row, "Product Family");
        String usage = usageSuffix(row);
        if (family.equals("Compute Instance")) {
            String type = col(row, "Instance Type");
            if (startsWithAny(type, EC2_FAMILIES)
                    && col(row, "Operating System").equals("Linux")
                    && col(row, "Tenancy").equals("Shared")
                    && col(row, "Pre Installed S/W").equals("NA")
                    && col(row, "CapacityStatus").equals("Used")
                    && col(row, "License Model").equals("No License required")
                    && col(row, "MarketOption").equals("OnDemand")) {
                return of("aws.ec2.instance",
                        "instanceType", type,
                        "instanceFamily", type.split("\\.")[0],
                        "vcpu", asInt(col(row, "vCPU")),
                        "memoryGiB", memoryGib(col(row, "Memory")),
                        "processorArchitecture", col(row, "Processor Architecture"),
                        "networkPerformance", col(row, "Network Performance"),
                        "operatingSystem", "Linux");
            }
            return null;
        }
        return switch (usage) {
            case "EBS:VolumeUsage.gp3" -> kind("aws.ebs.gp3", "storage");
            case "EBS:VolumeP-IOPS.gp3" -> kind("aws.ebs.gp3", "iops");
            case "EBS:VolumeP-Throughput.gp3" -> kind("aws.ebs.gp3", "throughput");
            case "EBS:SnapshotUsage" -> kind("aws.ebs.snapshot", "storage");
            case "NatGateway-Hours" -> kind("aws.vpc.nat_gateway", "hours");
            case "NatGateway-Bytes" -> kind("aws.vpc.nat_gateway", "data_processed");
            case "DataTransfer-Out-Bytes" -> family.equals("Data Transfer") ? kind("aws.data_transfer", "internet_out") : null;
            case "DataTransfer-Regional-Bytes" -> family.equals("Data Transfer") ? kind("aws.data_transfer", "inter_az") : null;
            default -> null;
        };
    }

    static Classification vpc(Map<String, String> row) {
        return switch (usageSuffix(row)) {
            case "PublicIPv4:InUseAddress" -> kind("aws.vpc.public_ipv4", "in_use");
            case "PublicIPv4:IdleAddress" -> kind("aws.vpc.public_ipv4", "idle");
            case "VpcEndpoint-Hours" -> kind("aws.vpc.interface_endpoint", "hours");
            case "VpcEndpoint-Bytes" -> kind("aws.vpc.interface_endpoint", "data_processed");
            default -> null;
        };
    }

    static Classification elb(Map<String, String> row) {
        // Só o uso padrão — fora Outposts, Trust Store e LCU reservada.
        String kind = switch (usageSuffix(row)) {
            case "LoadBalancerUsage" -> "hours";
            case "LCUUsage" -> "lcu";
            default -> null;
        };
        if (kind == null) {
            return null;
        }
        return switch (col(row, "Product Family")) {
            case "Load Balancer-Application" -> kind("aws.elb.alb", kind);
            case "Load Balancer-Network" -> kind("aws.elb.nlb", kind);
            default -> null;
        };
    }

    static Classification aurora(Map<String, String> row) {
        String family = col(row, "Product Family");
        String engine = col(row, "Database Engine");
        String usage = col(row, "usageType");
        // Fora: UnusedBox (reserva de capacidade não usada, mesmo preço e classe da instância).
        if (family.equals("Database Instance") && usage.contains("UnusedBox")) {
            return null;
        }
        if (family.equals("Database Instance") && startsWithAny(col(row, "Instance Type"), AURORA_CLASSES)) {
            return of("aws.aurora.instance",
                    "instanceClass", col(row, "Instance Type"),
                    "engine", engine,
                    "storageMode", usage.contains("InstanceUsageIOOptimized:") ? "io-optimized" : "standard",
                    "vcpu", asInt(col(row, "vCPU")),
                    "memoryGiB", memoryGib(col(row, "Memory")));
        }
        if (family.equals("Database Storage")) {
            String mode = switch (col(row, "Volume Type")) {
                case "General Purpose-Aurora" -> "standard";
                case "IO Optimized-Aurora" -> "io-optimized";
                default -> null;
            };
            return mode == null ? null : of("aws.aurora.storage", "engine", engine, "storageMode", mode);
        }
        if (family.equals("System Operation") && usageIs(row, "Aurora:StorageIOUsage")) {
            return of("aws.aurora.io", "engine", engine);
        }
        if (family.equals("Storage Snapshot") && usageIs(row, "Aurora:BackupUsage")) {
            return of("aws.aurora.backup", "engine", engine);
        }
        return null;
    }

    static Classification rds(Map<String, String> row) {
        String family = col(row, "Product Family");
        String engine = col(row, "Database Engine");
        String deployment = col(row, "Deployment Option");
        if (AURORA_ENGINES.contains(engine)) {
            return aurora(row);
        }
        if (family.equals("Storage Snapshot") && RDS_ENGINES.contains(engine) && usageIs(row, "RDS:ChargedBackupUsage")) {
            return of("aws.rds.backup", "engine", engine);
        }
        if (!RDS_ENGINES.contains(engine) || !RDS_DEPLOYMENTS.contains(deployment)) {
            return null;
        }
        if (family.equals("Database Instance") && col(row, "usageType").contains("UnusedBox")) {
            return null; // reserva de capacidade não usada (mesmo preço e classe da instância)
        }
        if (family.equals("Database Instance") && startsWithAny(col(row, "Instance Type"), RDS_CLASSES)) {
            return of("aws.rds.instance",
                    "instanceClass", col(row, "Instance Type"),
                    "engine", engine,
                    "deployment", deployment,
                    "vcpu", asInt(col(row, "vCPU")),
                    "memoryGiB", memoryGib(col(row, "Memory")));
        }
        if (family.equals("Database Storage") && col(row, "Volume Type").equals("General Purpose-GP3")) {
            return of("aws.rds.storage", "engine", engine, "deployment", deployment, "volumeType", "gp3");
        }
        return null;
    }

    static Classification s3(Map<String, String> row) {
        return switch (usageSuffix(row)) {
            case "TimedStorage-ByteHrs" -> kind("aws.s3.standard", "storage");
            case "Requests-Tier1" -> kind("aws.s3.standard", "put_requests");
            case "Requests-Tier2" -> kind("aws.s3.standard", "get_requests");
            default -> null;
        };
    }

    static Classification cloudFront(Map<String, String> row) {
        String usage = col(row, "usageType");
        if (usage.equals("Global-DataTransfer-Out-Bytes")) {
            return kind("aws.cloudfront", "free_data_out");
        }
        if (usage.equals("Global-Requests-Tier1")) {
            return kind("aws.cloudfront", "free_requests");
        }
        int dash = usage.indexOf('-');
        String geo = dash >= 0 ? usage.substring(0, dash) : usage;
        String rest = dash >= 0 ? usage.substring(dash + 1) : "";
        if (!CLOUDFRONT_GEOS.contains(geo)) {
            return null;
        }
        return switch (rest) {
            case "DataTransfer-Out-Bytes" -> of("aws.cloudfront", "kind", "data_out", "geo", geo);
            case "Requests-Tier2-HTTPS" -> of("aws.cloudfront", "kind", "https_requests", "geo", geo);
            default -> null;
        };
    }

    static Classification route53(Map<String, String> row) {
        return switch (col(row, "usageType")) {
            case "HostedZone" -> kind("aws.route53", "hosted_zone");
            case "DNS-Queries" -> kind("aws.route53", "queries");
            default -> null;
        };
    }

    static Classification waf(Map<String, String> row) {
        return kind("aws.waf", switch (usageSuffix(row)) {
            case "WebACLV2" -> "web_acl";
            case "RuleV2" -> "rule";
            case "RequestV2-Tier1" -> "requests";
            case "AMR-BotControl" -> "bot_control";
            default -> null;
        });
    }

    static Classification shield(Map<String, String> row) {
        return col(row, "usageType").equals("Shield-Monthly-Fee") ? kind("aws.shield", "monthly") : null;
    }

    static Classification kms(Map<String, String> row) {
        String usage = col(row, "usageType");
        if (usage.equals("Global-KMS-Requests")) {
            return kind("aws.kms", "free_requests");
        }
        // O prefixo é o código da Região (sa-east-1-KMS-Keys), não a sigla.
        if (usage.endsWith("-KMS-Keys")) {
            return kind("aws.kms", "key");
        }
        if (usage.endsWith("-KMS-Requests")) {
            return kind("aws.kms", "requests");
        }
        return null;
    }

    static Classification secrets(Map<String, String> row) {
        return kind("aws.secretsmanager", switch (usageSuffix(row)) {
            case "AWSSecretsManager-Secrets" -> "secret";
            case "AWSSecretsManagerAPIRequest" -> "api_requests";
            default -> null;
        });
    }

    static Classification ssm(Map<String, String> row) {
        return usageSuffix(row).equals("PS-Advanced-Param-Tier1") ? kind("aws.ssm", "advanced_parameter") : null;
    }

    static Classification cognito(Map<String, String> row) {
        if (col(row, "usageType").equals("Global-CognitoFreeTierMAU")) {
            return kind("aws.cognito", "free_mau");
        }
        String plan = switch (usageSuffix(row)) {
            case "CognitoLiteMAU" -> "LITE";
            case "CognitoEssentialsMAU" -> "ESSENTIALS";
            case "CognitoPlusMAU" -> "PLUS";
            default -> null;
        };
        return plan == null ? null : of("aws.cognito", "kind", "mau", "plan", plan);
    }

    static Classification lambda(Map<String, String> row) {
        String usage = col(row, "usageType");
        // Franquias globais primeiro: Global-Request tem o mesmo sufixo da cobrança.
        if (usage.equals("Global-Request")) {
            return kind("aws.lambda", "free_requests");
        }
        if (usage.equals("Global-Lambda-GB-Second")) {
            return kind("aws.lambda", "free_compute");
        }
        return switch (usageSuffix(row)) {
            case "Request" -> of("aws.lambda", "kind", "requests", "arch", "x86_64");
            case "Request-ARM" -> of("aws.lambda", "kind", "requests", "arch", "arm64");
            case "Lambda-GB-Second" -> of("aws.lambda", "kind", "compute", "arch", "x86_64");
            case "Lambda-GB-Second-ARM" -> of("aws.lambda", "kind", "compute", "arch", "arm64");
            default -> null;
        };
    }

    static Classification apiGateway(Map<String, String> row) {
        return usageSuffix(row).equals("ApiGatewayHttpRequest") ? kind("aws.apigw", "http_requests") : null;
    }

    static Classification ecs(Map<String, String> row) {
        return switch (usageSuffix(row)) {
            case "Fargate-vCPU-Hours:perCPU" -> of("aws.fargate", "kind", "vcpu", "arch", "x86_64");
            case "Fargate-ARM-vCPU-Hours:perCPU" -> of("aws.fargate", "kind", "vcpu", "arch", "arm64");
            case "Fargate-GB-Hours" -> of("aws.fargate", "kind", "memory", "arch", "x86_64");
            case "Fargate-ARM-GB-Hours" -> of("aws.fargate", "kind", "memory", "arch", "arm64");
            default -> null;
        };
    }

    static Classification eks(Map<String, String> row) {
        return kind("aws.eks", switch (usageSuffix(row)) {
            case "AmazonEKS-Hours:perCluster" -> "cluster";
            case "AmazonEKS-Hours:extendedSupport" -> "extended_support";
            default -> null;
        });
    }

    static Classification ecr(Map<String, String> row) {
        return usageSuffix(row).equals("TimedStorage-ByteHrs") ? kind("aws.ecr", "storage") : null;
    }

    static Classification elastiCache(Map<String, String> row) {
        String node = col(row, "Instance Type");
        String engine = col(row, "Cache Engine");
        if (col(row, "Product Family").equals("Cache Instance")
                && CACHE_ENGINES.contains(engine)
                && startsWithAny(node, CACHE_NODES)
                && usageIs(row, "NodeUsage:" + node)) {
            return of("aws.elasticache.node",
                    "nodeType", node,
                    "engine", engine,
                    "vcpu", asInt(col(row, "vCPU")),
                    "memoryGiB", memoryGib(col(row, "Memory")));
        }
        return null;
    }

    static Classification dynamoDb(Map<String, String> row) {
        return firstUsage(row, "aws.dynamodb",
                "ReadRequestUnits", "read_requests",
                "WriteRequestUnits", "write_requests",
                "ReadCapacityUnit-Hrs", "rcu",
                "WriteCapacityUnit-Hrs", "wcu",
                "TimedStorage-ByteHrs", "storage",
                "TimedPITRStorage-ByteHrs", "pitr");
    }

    static Classification sqs(Map<String, String> row) {
        if (col(row, "usageType").equals("Global-Requests")) {
            return kind("aws.sqs", "free_requests");
        }
        if (usageIs(row, "Requests-Tier1")) {
            return of("aws.sqs", "kind", "requests", "queueType", "standard");
        }
        if (usageIs(row, "Requests-FIFO-Tier1")) {
            return of("aws.sqs", "kind", "requests", "queueType", "fifo");
        }
        return null;
    }

    static Classification sns(Map<String, String> row) {
        return firstUsage(row, "aws.sns",
                "Requests-Tier1", "publishes",
                "F-Request-Tier1", "fifo_publishes",
                "F-DA-SQS", "fifo_deliveries");
    }

    static Classification efs(Map<String, String> row) {
        if (usageIs(row, "TimedStorage-ByteHrs")) {
            return kind("aws.efs", "storage");
        }
        if (usageIs(row, "ETDataAccess-Bytes")) {
            String access = col(row, "Access Type");
            if (access.equals("Read") || access.equals("Write")) {
                return kind("aws.efs", "elastic_" + access.toLowerCase());
            }
        }
        return null;
    }

    static Classification cloudWatch(Map<String, String> row) {
        return firstUsage(row, "aws.cloudwatch",
                "DataProcessing-Bytes", "logs_ingest",
                "VendedLog-Bytes", "logs_vended",
                "TimedStorage-ByteHrs", "logs_storage",
                "CW:MetricMonitorUsage", "metrics",
                "CW:AlarmMonitorUsage", "alarms");
    }

    static Classification cloudTrail(Map<String, String> row) {
        return firstUsage(row, "aws.cloudtrail",
                "DataEventsRecorded", "data_events",
                "PaidEventsRecorded", "paid_events");
    }

    static Classification backup(Map<String, String> row) {
        for (String target : List.of("EFS", "DynamoDB")) {
            if (usageIs(row, "WarmStorage-ByteHrs-" + target)) {
                return of("aws.backup", "kind", "warm_storage", "target", target);
            }
        }
        return null;
    }

    static Classification guardDuty(Map<String, String> row) {
        return firstUsage(row, "aws.guardduty",
                "PaidEventsAnalyzed", "events",
                "PaidEventsAnalyzed-Bytes", "logs");
    }

    static Classification securityHub(Map<String, String> row) {
        return usageIs(row, "PaidComplianceCheck") ? kind("aws.securityhub", "checks") : null;
    }

    static Classification config(Map<String, String> row) {
        return usageIs(row, "ConfigurationItemRecorded") ? kind("aws.config", "items") : null;
    }
}
