/**
 * Copyright (C) 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.document.service;

import org.jbpm.document.Document;

import java.io.File;

/**
 * Simple storage service definition
 */
public interface DocumentStorageService {

    /**
     * Method to store the uploaded file on the system
     * @param document      The document to store the content
     * @param content       The document content
     * @return              A Document
     */
    Document saveDocument(Document document, byte[] content);

    /**
     * Method to obtain a File for the given storage id
     * @param id            The Document id to obtain the Document
     * @return              The java.io.File identified with the id
     */
    Document getDocument(String id);

    /**
     * Deletes the File identified by the given id
     * @param id            The Document id to delete
     * @return              true if it was possible to remove, false if not
     */
    boolean deleteDocument(String id);

    /**
     * Deletes the File identified by the given id
     * @param document      The Document to delete
     * @return              true if it was possible to remove, false if not
     */
    boolean deleteDocument(Document document);
}
