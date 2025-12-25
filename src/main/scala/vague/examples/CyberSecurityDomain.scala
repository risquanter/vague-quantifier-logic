package vague.examples

import vague.datastore.*
import vague.datastore.RelationValue.*

/** Cybersecurity risk management domain for demonstrating vague quantifiers
  * 
  * Domain entities:
  * - Assets: IT infrastructure components (servers, databases, endpoints, networks)
  * - Risks: Security vulnerabilities and threats
  * - Mitigations: Security controls and patches
  * 
  * Relations:
  * - asset(x): x is an IT asset
  * - risk(r): r is a security risk
  * - mitigation(m): m is a security control/mitigation
  * - has_risk(x, r): asset x is affected by risk r
  * - has_mitigation(r, m): risk r is addressed by mitigation m
  * - critical_asset(x): x is a critical business asset
  * - critical_risk(r): r is a critical/high-severity risk
  * - high_value(x): x is a high-value asset (financial/data value)
  * - exploitable(r): r is easily exploitable
  * - patched(m): m is a deployed patch
  * 
  * This domain models realistic enterprise security scenarios for paper-style
  * vague quantifier queries like:
  * - Q[≥]^{3/4} x (asset(x), ∃r. (has_risk(x,r) ∧ critical_risk(r)))
  *   "At least 3/4 of assets have critical risks"
  * - Q[~]^{1/2} x (has_risk(x,r), has_mitigation(r,m))(r)
  *   "About half of risks have mitigations" (returns list of risks)
  */
object CyberSecurityDomain:
  
  /** Complete cybersecurity knowledge base */
  def kb: KnowledgeBase =
    val builder = KnowledgeBase.builder
    
    // ==================== Schema Definitions ====================
    
    // Unary relations (entity types and properties)
    builder.withUnaryRelation("asset")
    builder.withUnaryRelation("risk")
    builder.withUnaryRelation("mitigation")
    builder.withUnaryRelation("critical_asset")
    builder.withUnaryRelation("critical_risk")
    builder.withUnaryRelation("high_value")
    builder.withUnaryRelation("exploitable")
    builder.withUnaryRelation("patched")
    
    // Binary relations (relationships)
    builder.withBinaryRelation("has_risk")          // (asset, risk)
    builder.withBinaryRelation("has_mitigation")     // (risk, mitigation)
    builder.withBinaryRelation("affects")            // (risk, asset) - same as has_risk but reversed
    
    // ==================== Assets (24 total) ====================
    
    // Critical infrastructure (8 assets)
    val criticalAssets = List(
      "web_server_prod",
      "db_server_primary",
      "auth_server",
      "payment_gateway",
      "api_gateway",
      "backup_server",
      "domain_controller",
      "firewall_main"
    )
    
    // Non-critical infrastructure (8 assets)
    val standardAssets = List(
      "web_server_staging",
      "db_server_test",
      "dev_workstation_1",
      "dev_workstation_2",
      "employee_laptop_1",
      "employee_laptop_2",
      "conference_room_pc",
      "printer_network"
    )
    
    // High-value assets (8 assets - overlap with critical)
    val highValueAssets = List(
      "db_server_primary",      // customer data
      "payment_gateway",         // financial transactions
      "backup_server",           // business continuity
      "file_server_hr",          // sensitive HR data
      "crm_server",              // customer relationships
      "analytics_server",        // business intelligence
      "email_server",            // communications
      "vpn_gateway"              // remote access
    )
    
    val allAssets = (criticalAssets ++ standardAssets ++ highValueAssets).distinct
    
    allAssets.foreach(asset => builder.withFact("asset", asset))
    criticalAssets.foreach(asset => builder.withFact("critical_asset", asset))
    highValueAssets.foreach(asset => builder.withFact("high_value", asset))
    
    // ==================== Risks (20 total) ====================
    
    // Critical/High severity risks (12 risks)
    val criticalRisks = List(
      "sql_injection",
      "ransomware_threat",
      "zero_day_vuln",
      "privilege_escalation",
      "data_exfiltration",
      "ddos_vulnerability",
      "authentication_bypass",
      "remote_code_execution",
      "credential_stuffing",
      "cross_site_scripting",
      "insecure_deserialization",
      "xml_external_entity"
    )
    
    // Medium/Low severity risks (8 risks)
    val standardRisks = List(
      "weak_ssl_cipher",
      "missing_security_headers",
      "outdated_library",
      "information_disclosure",
      "session_fixation",
      "clickjacking",
      "cors_misconfiguration",
      "verbose_error_messages"
    )
    
    val allRisks = criticalRisks ++ standardRisks
    
    allRisks.foreach(risk => builder.withFact("risk", risk))
    criticalRisks.foreach(risk => builder.withFact("critical_risk", risk))
    
    // Exploitable risks (subset of all risks - 15 total)
    val exploitableRisks = List(
      "sql_injection",
      "ransomware_threat",
      "privilege_escalation",
      "authentication_bypass",
      "remote_code_execution",
      "credential_stuffing",
      "cross_site_scripting",
      "weak_ssl_cipher",
      "missing_security_headers",
      "outdated_library",
      "information_disclosure",
      "session_fixation",
      "clickjacking",
      "cors_misconfiguration",
      "verbose_error_messages"
    )
    
    exploitableRisks.foreach(risk => builder.withFact("exploitable", risk))
    
    // ==================== Mitigations (16 total) ====================
    
    val mitigations = List(
      "waf_rules",                    // web application firewall
      "input_validation",             // code-level defense
      "patch_cve_2024_001",          // specific patches
      "patch_cve_2024_002",
      "patch_cve_2024_003",
      "mfa_enforcement",              // authentication controls
      "network_segmentation",         // architecture
      "encryption_at_rest",           // data protection
      "encryption_in_transit",
      "rate_limiting",                // availability protection
      "security_headers",             // browser security
      "least_privilege_policy",       // access control
      "backup_rotation",              // recovery
      "intrusion_detection",          // monitoring
      "security_training",            // human factor
      "vulnerability_scanning"        // proactive detection
    )
    
    mitigations.foreach(mit => builder.withFact("mitigation", mit))
    
    // Patched mitigations (deployed fixes - 10 out of 16)
    val patchedMitigations = List(
      "patch_cve_2024_001",
      "patch_cve_2024_002",
      "patch_cve_2024_003",
      "waf_rules",
      "mfa_enforcement",
      "encryption_at_rest",
      "encryption_in_transit",
      "rate_limiting",
      "intrusion_detection",
      "vulnerability_scanning"
    )
    
    patchedMitigations.foreach(mit => builder.withFact("patched", mit))
    
    // ==================== has_risk(asset, risk) ====================
    // Most assets have multiple risks, creating realistic risk distribution
    
    // Critical assets with critical risks (ensures q₁ will be satisfied)
    // Target: ~18-20 of 24 total assets have critical risks (75-83%)
    List(
      // Critical infrastructure (all 8 have critical risks)
      ("web_server_prod", "sql_injection"),
      ("web_server_prod", "cross_site_scripting"),
      ("web_server_prod", "ddos_vulnerability"),
      ("db_server_primary", "sql_injection"),
      ("db_server_primary", "data_exfiltration"),
      ("auth_server", "authentication_bypass"),
      ("auth_server", "credential_stuffing"),
      ("payment_gateway", "data_exfiltration"),
      ("payment_gateway", "ddos_vulnerability"),
      ("api_gateway", "authentication_bypass"),
      ("api_gateway", "remote_code_execution"),
      ("backup_server", "ransomware_threat"),
      ("domain_controller", "privilege_escalation"),
      ("domain_controller", "zero_day_vuln"),
      ("firewall_main", "ddos_vulnerability"),
      
      // Non-critical with critical risks (5 of 8 - brings total to 13)
      ("web_server_staging", "sql_injection"),
      ("db_server_test", "data_exfiltration"),
      ("dev_workstation_1", "ransomware_threat"),
      ("employee_laptop_1", "credential_stuffing"),
      ("employee_laptop_2", "ransomware_threat"),
      
      // High-value with critical risks (adds 5 more unique - total 18)
      ("file_server_hr", "data_exfiltration"),
      ("file_server_hr", "ransomware_threat"),
      ("crm_server", "sql_injection"),
      ("analytics_server", "data_exfiltration"),
      ("email_server", "credential_stuffing"),
      ("vpn_gateway", "authentication_bypass"),
      
      // Non-critical assets with standard risks only
      ("dev_workstation_2", "outdated_library"),
      ("conference_room_pc", "weak_ssl_cipher"),
      ("conference_room_pc", "missing_security_headers"),
      ("printer_network", "information_disclosure"),
      
      // High-value with mixed risks
      ("file_server_hr", "weak_ssl_cipher"),
      ("crm_server", "session_fixation"),
      ("analytics_server", "cors_misconfiguration"),
      ("email_server", "weak_ssl_cipher"),
      ("vpn_gateway", "outdated_library")
    ).foreach { case (asset, risk) =>
      builder.withFact("has_risk", asset, risk)
      builder.withFact("affects", risk, asset)
    }
    
    // ==================== has_mitigation(risk, mitigation) ====================
    // About half of risks have mitigations (10-11 out of 20 for q₂)
    
    List(
      // Critical risks with mitigations (6 of 12 mitigated)
      ("sql_injection", "waf_rules"),
      ("sql_injection", "input_validation"),
      ("ransomware_threat", "backup_rotation"),
      ("ransomware_threat", "network_segmentation"),
      ("zero_day_vuln", "patch_cve_2024_001"),
      ("zero_day_vuln", "intrusion_detection"),
      ("authentication_bypass", "mfa_enforcement"),
      ("authentication_bypass", "least_privilege_policy"),
      ("ddos_vulnerability", "rate_limiting"),
      ("remote_code_execution", "patch_cve_2024_002"),
      
      // Standard risks with mitigations (4 of 8 mitigated)
      ("weak_ssl_cipher", "encryption_in_transit"),
      ("missing_security_headers", "security_headers"),
      ("outdated_library", "patch_cve_2024_003"),
      ("outdated_library", "vulnerability_scanning"),
      ("information_disclosure", "least_privilege_policy"),
      
      // Total: 10 risks have mitigations (50%)
      // Unmitigated critical: privilege_escalation, data_exfiltration, 
      //   credential_stuffing, cross_site_scripting, insecure_deserialization,
      //   xml_external_entity (6 critical unmitigated)
      // Unmitigated standard: session_fixation, clickjacking, 
      //   cors_misconfiguration, verbose_error_messages (4 standard unmitigated)
    ).foreach { case (risk, mitigation) =>
      builder.withFact("has_mitigation", risk, mitigation)
    }
    
    builder.build()
  
  /** Print domain statistics */
  def printSummary(): Unit =
    val domain = kb
    println("\n" + "="*70)
    println("CYBERSECURITY RISK MANAGEMENT DOMAIN")
    println("="*70)
    
    val assets = domain.getDomain("asset").size
    val criticalAssets = domain.getDomain("critical_asset").size
    val highValueAssets = domain.getDomain("high_value").size
    val risks = domain.getDomain("risk").size
    val criticalRisks = domain.getDomain("critical_risk").size
    val exploitableRisks = domain.getDomain("exploitable").size
    val mitigations = domain.getDomain("mitigation").size
    val patchedMits = domain.getDomain("patched").size
    
    println(s"\nAssets: $assets total")
    println(s"  - Critical: $criticalAssets")
    println(s"  - High-value: $highValueAssets")
    
    println(s"\nRisks: $risks total")
    println(s"  - Critical/High severity: $criticalRisks")
    println(s"  - Exploitable: $exploitableRisks")
    
    println(s"\nMitigations: $mitigations total")
    println(s"  - Deployed/Patched: $patchedMits")
    
    val hasRiskFacts = domain.query("has_risk", List(None, None)).size
    val hasMitigationFacts = domain.query("has_mitigation", List(None, None)).size
    
    println(s"\nRelationships:")
    println(s"  - has_risk: $hasRiskFacts facts")
    println(s"  - has_mitigation: $hasMitigationFacts facts")
    
    // Calculate key metrics for example queries
    val assetsWithCriticalRisks = domain.query("has_risk", List(None, None))
      .map(_.values(0))
      .toSet
      .count { asset =>
        domain.query("has_risk", List(Some(asset), None))
          .exists { tuple =>
            val risk = tuple.values(1)
            domain.query("critical_risk", List(Some(risk))).nonEmpty
          }
      }
    
    val risksWithMitigations = domain.query("has_mitigation", List(None, None))
      .map(_.values(0))
      .toSet
      .size
    
    println(s"\nKey Metrics:")
    println(s"  - Assets with critical risks: $assetsWithCriticalRisks / $assets (${assetsWithCriticalRisks * 100 / assets}%)")
    println(s"  - Risks with mitigations: $risksWithMitigations / $risks (${risksWithMitigations * 100 / risks}%)")
    
    val criticalAssetsWithUnmitigatedRisks = domain.query("critical_asset", List(None))
      .map(_.values(0))
      .count { critAsset =>
        domain.query("has_risk", List(Some(critAsset), None))
          .exists { tuple =>
            val risk = tuple.values(1)
            domain.query("has_mitigation", List(Some(risk), None)).isEmpty
          }
      }
    
    println(s"  - Critical assets with unmitigated risks: $criticalAssetsWithUnmitigatedRisks / $criticalAssets (${criticalAssetsWithUnmitigatedRisks * 100 / criticalAssets}%)")
    
    println("\n" + "="*70 + "\n")

end CyberSecurityDomain
