# =============================================================
#  monitoring.tf — Alerta por email (OCI Monitoring + Notifications,
#  Always Free) quando o backend da API fica unhealthy no Load Balancer.
#
#  Só o alerta entra nesta sprint — recriar a VM sozinha quando o alarme
#  dispara (auto-remediação) fica fora de propósito, por ser mais
#  arriscado (loop de recriação mal projetado). Ver security.md.
#
#  Depois do apply, a OCI manda um e-mail de confirmação pra
#  var.alert_email — a inscrição fica "Pending" até esse link ser
#  clicado, e nenhum alerta chega antes disso.
# =============================================================

resource "oci_ons_notification_topic" "alerts" {
  compartment_id = oci_identity_compartment.app.id
  name           = "rede-studio-alerts"
}

resource "oci_ons_subscription" "alerts_email" {
  compartment_id = oci_identity_compartment.app.id
  topic_id       = oci_ons_notification_topic.alerts.id
  protocol       = "EMAIL"
  endpoint       = var.alert_email
}

# Dispara se o backend HTTPS (443) do LB ficar unhealthy por mais de 1
# minuto seguido — cobre tanto "app caiu" quanto "VM inteira travou".
resource "oci_monitoring_alarm" "api_backend_unhealthy" {
  compartment_id        = oci_identity_compartment.app.id
  display_name          = "rede-studio-api-backend-unhealthy"
  metric_compartment_id = oci_identity_compartment.app.id
  namespace             = "oci_lbaas"
  query                 = "unhealthyBackendServers[1m]{resourceId = \"${oci_load_balancer_load_balancer.api.id}\", backendSetName = \"${oci_load_balancer_backend_set.api_https.name}\"}.max() > 0"
  severity              = "CRITICAL"
  destinations          = [oci_ons_notification_topic.alerts.id]
  is_enabled            = true
  pending_duration      = "PT1M"
  body                  = "Backend da API (porta 443) ficou unhealthy no Load Balancer rede-studio-lb — provavelmente app ou VM caiu."
  # "" (nao omitir -- o atributo eh Optional+Computed no provider da OCI,
  # entao so remover a linha nao gera diff nenhum, o valor antigo fica
  # "grudado" no state) notifica so na MUDANCA de estado (OK->FIRING e
  # FIRING->OK), uma vez cada. Com "PT30M" (valor anterior), reenviava o
  # mesmo alerta a cada 30min inteiro enquanto o alarme seguisse firing --
  # o que aconteceu de verdade em 2026-09 enquanto o backend 443 ficou sem
  # certificado TLS esperando o rate limit do Let's Encrypt liberar.
  repeat_notification_duration = ""
}
