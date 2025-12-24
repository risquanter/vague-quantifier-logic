package vague.datastore

import vague.datastore.RelationValue.*

/** Risk Management Domain - Phase 13.1
  * 
  * Example knowledge base for risk management, containing:
  * - Components (software/hardware modules)
  * - Risks (potential threats)
  * - Mitigations (countermeasures)
  * - Relationships between them
  * 
  * This domain is used to demonstrate vague quantifier queries like:
  * - "Do almost all critical components have mitigations?"
  * - "Which risks affect about half of all components?"
  * - "Are at least 3/4 of components adequately protected?"
  */
object RiskDomain:
  
  // ==================== Schema Definition ====================
  
  val componentRelation = Relation.unary("component")
  val riskRelation = Relation.unary("risk")
  val mitigationRelation = Relation.unary("mitigation")
  val hasRiskRelation = Relation.binary("has_risk")
  val hasMitigationRelation = Relation.binary("has_mitigation")
  val mitigatesRelation = Relation.binary("mitigates")
  
  // Optional: attributes for richer queries
  val criticalComponentRelation = Relation.unary("critical_component")
  val highSeverityRiskRelation = Relation.unary("high_severity")
  
  // ==================== Sample Data ====================
  
  /** Component IDs */
  val components = Set(
    "auth_module", "database", "web_server", "api_gateway", "cache_layer",
    "load_balancer", "payment_processor", "email_service", "logging_system",
    "monitoring_service", "backup_system", "encryption_module", "firewall",
    "session_manager", "user_interface", "data_pipeline", "message_queue",
    "file_storage", "search_engine", "analytics_engine", "notification_service",
    "cdn", "config_service", "secret_manager", "scheduler"
  )
  
  /** Risk IDs */
  val risks = Set(
    "sql_injection", "xss_attack", "csrf", "ddos", "data_breach",
    "privilege_escalation", "session_hijacking", "code_injection",
    "buffer_overflow", "race_condition", "memory_leak", "unauthorized_access",
    "data_corruption", "service_disruption", "credential_theft",
    "man_in_the_middle", "replay_attack", "zero_day_exploit"
  )
  
  /** Mitigation IDs */
  val mitigations = Set(
    "input_validation", "output_encoding", "csrf_tokens", "rate_limiting",
    "encryption_at_rest", "encryption_in_transit", "access_control",
    "code_review", "static_analysis", "penetration_testing",
    "security_monitoring", "incident_response", "backup_recovery",
    "patch_management", "least_privilege"
  )
  
  /** Critical components (subset of components) */
  val criticalComponents = Set(
    "auth_module", "database", "payment_processor", "encryption_module",
    "secret_manager", "firewall", "backup_system"
  )
  
  /** High severity risks (subset of risks) */
  val highSeverityRisks = Set(
    "sql_injection", "data_breach", "privilege_escalation",
    "unauthorized_access", "credential_theft", "zero_day_exploit"
  )
  
  /** Component-Risk relationships
    * 
    * Maps which components are exposed to which risks.
    * Realistic distribution: most components have 3-6 risks.
    */
  val componentRisks: Map[String, Set[String]] = Map(
    "auth_module" -> Set("sql_injection", "privilege_escalation", "session_hijacking", "credential_theft"),
    "database" -> Set("sql_injection", "data_breach", "unauthorized_access", "data_corruption"),
    "web_server" -> Set("ddos", "xss_attack", "csrf", "code_injection"),
    "api_gateway" -> Set("ddos", "unauthorized_access", "man_in_the_middle", "replay_attack"),
    "cache_layer" -> Set("data_corruption", "memory_leak", "service_disruption"),
    "load_balancer" -> Set("ddos", "service_disruption", "race_condition"),
    "payment_processor" -> Set("data_breach", "man_in_the_middle", "credential_theft", "unauthorized_access"),
    "email_service" -> Set("xss_attack", "code_injection", "service_disruption"),
    "logging_system" -> Set("data_breach", "unauthorized_access", "data_corruption"),
    "monitoring_service" -> Set("unauthorized_access", "service_disruption"),
    "backup_system" -> Set("data_breach", "unauthorized_access", "data_corruption"),
    "encryption_module" -> Set("privilege_escalation", "zero_day_exploit", "memory_leak"),
    "firewall" -> Set("ddos", "unauthorized_access", "zero_day_exploit"),
    "session_manager" -> Set("session_hijacking", "csrf", "replay_attack"),
    "user_interface" -> Set("xss_attack", "csrf", "code_injection"),
    "data_pipeline" -> Set("data_corruption", "race_condition", "memory_leak"),
    "message_queue" -> Set("service_disruption", "race_condition", "memory_leak"),
    "file_storage" -> Set("unauthorized_access", "data_breach", "data_corruption"),
    "search_engine" -> Set("sql_injection", "service_disruption", "memory_leak"),
    "analytics_engine" -> Set("data_breach", "service_disruption"),
    "notification_service" -> Set("service_disruption", "code_injection"),
    "cdn" -> Set("ddos", "man_in_the_middle"),
    "config_service" -> Set("unauthorized_access", "data_corruption"),
    "secret_manager" -> Set("data_breach", "unauthorized_access", "credential_theft", "privilege_escalation"),
    "scheduler" -> Set("service_disruption", "race_condition")
  )
  
  /** Component-Mitigation relationships
    * 
    * Maps which mitigations are deployed for which components.
    * Realistic: critical components have more mitigations.
    */
  val componentMitigations: Map[String, Set[String]] = Map(
    "auth_module" -> Set("input_validation", "access_control", "code_review", "security_monitoring"),
    "database" -> Set("input_validation", "encryption_at_rest", "access_control", "backup_recovery"),
    "web_server" -> Set("input_validation", "output_encoding", "rate_limiting", "security_monitoring"),
    "api_gateway" -> Set("rate_limiting", "encryption_in_transit", "access_control", "security_monitoring"),
    "cache_layer" -> Set("access_control", "security_monitoring"),
    "load_balancer" -> Set("rate_limiting", "security_monitoring"),
    "payment_processor" -> Set("encryption_at_rest", "encryption_in_transit", "access_control", "security_monitoring", "penetration_testing"),
    "email_service" -> Set("output_encoding", "rate_limiting", "security_monitoring"),
    "logging_system" -> Set("encryption_at_rest", "access_control"),
    "monitoring_service" -> Set("access_control", "security_monitoring"),
    "backup_system" -> Set("encryption_at_rest", "access_control", "backup_recovery"),
    "encryption_module" -> Set("code_review", "static_analysis", "penetration_testing"),
    "firewall" -> Set("rate_limiting", "security_monitoring", "patch_management"),
    "session_manager" -> Set("csrf_tokens", "encryption_in_transit", "security_monitoring"),
    "user_interface" -> Set("output_encoding", "csrf_tokens", "code_review"),
    "data_pipeline" -> Set("access_control", "security_monitoring"),
    "message_queue" -> Set("access_control", "security_monitoring"),
    "file_storage" -> Set("encryption_at_rest", "access_control", "backup_recovery"),
    "search_engine" -> Set("input_validation", "security_monitoring"),
    "analytics_engine" -> Set("access_control", "security_monitoring"),
    "notification_service" -> Set("rate_limiting", "security_monitoring"),
    "cdn" -> Set("rate_limiting", "encryption_in_transit"),
    "config_service" -> Set("access_control", "backup_recovery"),
    "secret_manager" -> Set("encryption_at_rest", "encryption_in_transit", "access_control", "least_privilege", "security_monitoring"),
    "scheduler" -> Set("access_control", "security_monitoring")
  )
  
  /** Mitigation-Risk relationships
    * 
    * Maps which mitigations address which risks.
    */
  val mitigationRisks: Map[String, Set[String]] = Map(
    "input_validation" -> Set("sql_injection", "xss_attack", "code_injection"),
    "output_encoding" -> Set("xss_attack", "code_injection"),
    "csrf_tokens" -> Set("csrf", "replay_attack"),
    "rate_limiting" -> Set("ddos", "brute_force"),
    "encryption_at_rest" -> Set("data_breach", "unauthorized_access"),
    "encryption_in_transit" -> Set("man_in_the_middle", "data_breach"),
    "access_control" -> Set("unauthorized_access", "privilege_escalation", "data_breach"),
    "code_review" -> Set("code_injection", "buffer_overflow", "memory_leak"),
    "static_analysis" -> Set("code_injection", "buffer_overflow", "race_condition"),
    "penetration_testing" -> Set("zero_day_exploit", "privilege_escalation"),
    "security_monitoring" -> Set("data_breach", "unauthorized_access", "service_disruption"),
    "incident_response" -> Set("data_breach", "service_disruption", "zero_day_exploit"),
    "backup_recovery" -> Set("data_corruption", "service_disruption"),
    "patch_management" -> Set("zero_day_exploit", "buffer_overflow"),
    "least_privilege" -> Set("privilege_escalation", "unauthorized_access")
  )
  
  // ==================== Knowledge Base Construction ====================
  
  /** Build the complete risk management knowledge base */
  def createKnowledgeBase: KnowledgeBase =
    val builder = KnowledgeBase.builder
    
    // Add schema
    builder
      .withRelation(componentRelation)
      .withRelation(riskRelation)
      .withRelation(mitigationRelation)
      .withRelation(hasRiskRelation)
      .withRelation(hasMitigationRelation)
      .withRelation(mitigatesRelation)
      .withRelation(criticalComponentRelation)
      .withRelation(highSeverityRiskRelation)
    
    // Add component facts
    components.foreach(c => builder.withFact("component", c))
    
    // Add risk facts
    risks.foreach(r => builder.withFact("risk", r))
    
    // Add mitigation facts
    mitigations.foreach(m => builder.withFact("mitigation", m))
    
    // Add critical component facts
    criticalComponents.foreach(c => builder.withFact("critical_component", c))
    
    // Add high severity risk facts
    highSeverityRisks.foreach(r => builder.withFact("high_severity", r))
    
    // Add component-risk relationships
    componentRisks.foreach { case (comp, riskSet) =>
      riskSet.foreach(risk => builder.withFact("has_risk", comp, risk))
    }
    
    // Add component-mitigation relationships
    componentMitigations.foreach { case (comp, mitSet) =>
      mitSet.foreach(mit => builder.withFact("has_mitigation", comp, mit))
    }
    
    // Add mitigation-risk relationships
    mitigationRisks.foreach { case (mit, riskSet) =>
      riskSet.foreach(risk => builder.withFact("mitigates", mit, risk))
    }
    
    builder.build()
  
  /** Get a summary of the risk domain */
  def summary: String =
    val kb = createKnowledgeBase
    val sb = new StringBuilder
    sb.append("Risk Management Domain\n")
    sb.append("=" * 50 + "\n\n")
    sb.append(s"Components: ${components.size}\n")
    sb.append(s"  Critical: ${criticalComponents.size}\n")
    sb.append(s"Risks: ${risks.size}\n")
    sb.append(s"  High Severity: ${highSeverityRisks.size}\n")
    sb.append(s"Mitigations: ${mitigations.size}\n\n")
    sb.append(kb.stats)
    sb.toString
