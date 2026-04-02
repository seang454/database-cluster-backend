package com.example.demo.cluster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class ClusterPlatformConfig {

	@Column(name = "ingress_enabled")
	private Boolean ingressEnabled = Boolean.TRUE;

	@Column(name = "ingress_class_name", length = 50)
	private String ingressClassName = "nginx";

	@Column(name = "external_tcp_proxy_enabled")
	private Boolean externalTcpProxyEnabled = Boolean.TRUE;

	@Column(name = "external_secrets_enabled")
	private Boolean externalSecretsEnabled = Boolean.TRUE;

	@Column(name = "cloudflare_enabled")
	private Boolean cloudflareEnabled = Boolean.FALSE;

	@Column(name = "cloudflare_zone_name", length = 255)
	private String cloudflareZoneName;

	@Column(name = "vault_enabled")
	private Boolean vaultEnabled = Boolean.TRUE;
}
