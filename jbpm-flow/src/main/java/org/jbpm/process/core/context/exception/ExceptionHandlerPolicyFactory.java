/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
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
package org.jbpm.process.core.context.exception;

import java.util.ArrayList;
import java.util.Collection;

public class ExceptionHandlerPolicyFactory {

    private ExceptionHandlerPolicyFactory() {
    }

    private static Collection<ExceptionHandlerPolicy> policies = new ArrayList<>();

    static {
        policies.add(new ErrorCodeExceptionPolicy());
        policies.add(new IsExceptionPolicy());
        policies.add(new MessageContentEqualsExceptionPolicy());
        policies.add(new IsWrappedExceptionPolicy());
        policies.add(new MessageContentRegexExceptionPolicy());
        policies.add(new IsChildExceptionPolicy());
    }

    public static Collection<ExceptionHandlerPolicy> getHandlerPolicies() {
        return policies;
    }
}
