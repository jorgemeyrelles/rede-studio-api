# =============================================================
#  loadbalancer.tf — Load Balancer Flexible (Always Free, 10 Mbps)
#
#  TCP passthrough na 80 E na 443 — TLS continua terminando no nginx da
#  VM via Certbot, exatamente como hoje. A Security List da VM NÃO é
#  restringida nesta fase (continua aceitando 80/443 de 0.0.0.0/0 além
#  do LB) — isso fica pra uma fase futura, feita com tempo de sobra pra
#  validar a renovação antes de restringir de vez (decisão consciente,
#  ver `.claude/plans/cicd-github-actions.md`).
#
#  IMPORTANTE (aprendido na prática, não só teoria): o listener de 80 é
#  obrigatório mesmo com a VM ainda aceitando 80 direto — o Certbot
#  valida o desafio HTTP-01 através do NOME DNS configurado
#  (duckdns_hostname), não do IP da VM. Assim que o DNS passou a apontar
#  pro LB (Fase 2), a falta do listener 80 aqui quebrou a emissão do
#  certificado na primeira recriação da VM depois disso (Let's Encrypt
#  tentou alcançar o desafio através do LB e não tinha nada escutando lá
#  — "Timeout during connect"). "A VM continuar aberta direto" não ajuda
#  nesse caso porque o Certbot nunca usa o IP direto, só o hostname.
#
#  DNS deve apontar pro IP deste LB (ver output `lb_public_ip`).
# =============================================================

resource "oci_core_public_ip" "lb" {
  compartment_id = oci_identity_compartment.app.id
  display_name   = "rede-studio-lb-reserved-ip"
  lifetime       = "RESERVED"

  # Depois que o LB usa este IP via reserved_ips, a própria OCI passa a
  # gerenciar o vínculo com a private IP do LB — sem isso o Terraform
  # tenta "desassociar" esse campo de volta pra null a cada apply, e a
  # API rejeita ("managed by <load balancer>"). Mesmo padrão já usado em
  # vault.tf pro conteúdo do secret (outro sistema é dono desse campo).
  lifecycle {
    ignore_changes = [private_ip_id]
  }
}

resource "oci_load_balancer_load_balancer" "api" {
  compartment_id = oci_identity_compartment.app.id
  display_name   = "rede-studio-lb"
  shape          = "flexible"

  shape_details {
    minimum_bandwidth_in_mbps = 10
    maximum_bandwidth_in_mbps = 10
  }

  subnet_ids = [oci_core_subnet.public.id]

  reserved_ips {
    id = oci_core_public_ip.lb.id
  }
}

resource "oci_load_balancer_backend_set" "api_https" {
  name             = "rede-studio-api-bes"
  load_balancer_id = oci_load_balancer_load_balancer.api.id
  policy           = "ROUND_ROBIN"

  # HTTPS não é aceito pelo enum do health_checker nesta versão do
  # provider (6.37.0, bem atrás da atual — "No enum constant for HTTPS"
  # na API). TCP só confirma que a porta 443 aceita conexão (não valida
  # o corpo da resposta) — cobre o caso principal (VM caída/porta
  # fechada). Revisitar pra HTTPS de verdade quando o provider for
  # atualizado.
  health_checker {
    protocol          = "TCP"
    port              = 443
    interval_ms       = 10000
    timeout_in_millis = 5000
    retries           = 3
  }
}

resource "oci_load_balancer_backend" "api" {
  load_balancer_id = oci_load_balancer_load_balancer.api.id
  backendset_name  = oci_load_balancer_backend_set.api_https.name
  ip_address       = data.oci_core_private_ips.api.private_ips[0].ip_address
  port             = 443
}

resource "oci_load_balancer_listener" "api_https" {
  load_balancer_id         = oci_load_balancer_load_balancer.api.id
  name                     = "https-passthrough"
  default_backend_set_name = oci_load_balancer_backend_set.api_https.name
  port                     = 443
  protocol                 = "TCP"

  connection_configuration {
    idle_timeout_in_seconds = 60
  }
}

# --- Porta 80: obrigatória pro desafio HTTP-01 do Certbot (ver nota no
#     topo do arquivo) e pro redirect 80->443 que o nginx já faz sozinho
#     depois que o certificado existe. Passthrough puro, igual a 443.

resource "oci_load_balancer_backend_set" "api_http" {
  name             = "rede-studio-api-bes-http"
  load_balancer_id = oci_load_balancer_load_balancer.api.id
  policy           = "ROUND_ROBIN"

  health_checker {
    protocol          = "TCP"
    port              = 80
    interval_ms       = 10000
    timeout_in_millis = 5000
    retries           = 3
  }
}

resource "oci_load_balancer_backend" "api_http" {
  load_balancer_id = oci_load_balancer_load_balancer.api.id
  backendset_name  = oci_load_balancer_backend_set.api_http.name
  ip_address       = data.oci_core_private_ips.api.private_ips[0].ip_address
  port             = 80
}

resource "oci_load_balancer_listener" "api_http" {
  load_balancer_id         = oci_load_balancer_load_balancer.api.id
  name                     = "http-passthrough"
  default_backend_set_name = oci_load_balancer_backend_set.api_http.name
  port                     = 80
  protocol                 = "TCP"

  connection_configuration {
    idle_timeout_in_seconds = 60
  }
}
