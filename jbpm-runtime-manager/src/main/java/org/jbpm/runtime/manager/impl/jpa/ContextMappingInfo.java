/*
 * Copyright 2013 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.runtime.manager.impl.jpa;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.SequenceGenerator;
import javax.persistence.Version;

/**
 * Main entity for runtime manager to keep track of what context is bound to what <code>KieSession</code>
 * it provides as well two queries to fetch required information.
 * <ul>
 *  <li>FindContextMapingByContextId</li>
 *  <li>FindContextMapingByKSessionId</li>
 * </ul>
 * This entity must be included in persistence.xml when "Per Process Instance" strategy is used.
 */
@Entity
@SequenceGenerator(name="contextMappingInfoIdSeq", sequenceName="CONTEXT_MAPPING_INFO_ID_SEQ")
@NamedQueries(value=
    {@NamedQuery(name="FindContextMapingByContextId", 
                query="from ContextMappingInfo where contextId = :contextId"),
                @NamedQuery(name="FindContextMapingByKSessionId", 
                query="from ContextMappingInfo where ksessionId = :ksessionId")})
public class ContextMappingInfo implements Serializable {

    private static final long serialVersionUID = 533985957655465840L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator="contextMappingInfoIdSeq")
    private Long mappingId;

    @Version
    @Column(name = "OPTLOCK")
    private int version;
    
    @Column(name="CONTEXT_ID", nullable=false)
    private String contextId;
    @Column(name="KSESSION_ID", nullable=false)
    private Integer ksessionId;
    
    public ContextMappingInfo() {
        
    }

    public ContextMappingInfo(String contextId, Integer ksessionId) {
        this.contextId = contextId;
        this.ksessionId = ksessionId;
    }

    public Long getMappingId() {
        return mappingId;
    }

    public void setMappingId(Long mappingId) {
        this.mappingId = mappingId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public Integer getKsessionId() {
        return ksessionId;
    }

    public void setKsessionId(Integer ksessionId) {
        this.ksessionId = ksessionId;
    }
    
    

}
