package br.com.redestudio.entities;

/** Tipo de um modelo do catálogo IAM ({@code cloud_iam_templates}). */
public enum IamTemplateKind {
    /** Modelo de grupo de usuários (ex.: administradores de rede). */
    GROUP,
    /** Modelo de role assumida por um serviço (ex.: EC2 com SSM). */
    ROLE,
    /** Política gerenciada pelo provedor, anexável a grupos e roles. */
    POLICY
}
