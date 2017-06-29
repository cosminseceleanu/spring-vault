/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.vault.authentication;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

/**
 * Adapts tokens created by a {@link ClientAuthentication} to a {@link LoginToken}. Allows
 * decoration of a {@link ClientAuthentication} object to perform a self-lookup after
 * token retrieval to obtain the remaining TTL and renewability.
 * <p>
 * Using this adapter decrements the usage counter for the created token.
 *
 * @author Mark Paluch
 * @since 1.1
 * @see LoginToken
 */
public class LoginTokenAdapter implements ClientAuthentication {

	private final ClientAuthentication delegate;

	private final RestOperations restOperations;

	/**
	 * Create a new {@link LoginTokenAdapter} given {@link ClientAuthentication} to
	 * decorate and {@link RestOperations}.
	 *
	 * @param delegate must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 */
	public LoginTokenAdapter(ClientAuthentication delegate, RestOperations restOperations) {

		Assert.notNull(delegate, "ClientAuthentication delegate must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");

		this.delegate = delegate;
		this.restOperations = restOperations;
	}

	@Override
	public LoginToken login() throws VaultException {
		return augmentWithSelfLookup(delegate.login());
	}

	private LoginToken augmentWithSelfLookup(VaultToken token) {

		Map<String, Object> data = lookupSelf(token);

		Boolean renewable = (Boolean) data.get("renewable");
		Number ttl = (Number) data.get("ttl");

		if (renewable != null && renewable) {
			return LoginToken.renewable(token.toCharArray(),
					ttl == null ? 0 : ttl.longValue());
		}

		return LoginToken.of(token.toCharArray(), ttl == null ? 0 : ttl.longValue());
	}

	private Map<String, Object> lookupSelf(VaultToken token) {

		try {
			ResponseEntity<VaultResponse> entity = restOperations.exchange(
					"auth/token/lookup-self", HttpMethod.GET, new HttpEntity<Object>(
							VaultHttpHeaders.from(token)), VaultResponse.class);

			return entity.getBody().getData();
		}
		catch (HttpStatusCodeException e) {
			throw new VaultException(String.format(
					"Cannot self-lookup Token from Cubbyhole: %s %s", e.getStatusCode(),
					VaultResponses.getError(e.getResponseBodyAsString())));
		}
	}
}
