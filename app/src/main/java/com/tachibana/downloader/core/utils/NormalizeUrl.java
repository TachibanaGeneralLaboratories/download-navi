/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tachibana.downloader.core.utils;

import com.tachibana.downloader.core.exception.NormalizeUrlException;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.mola.galimatias.GalimatiasParseException;
import io.mola.galimatias.IPv6Address;
import io.mola.galimatias.URL;
import io.mola.galimatias.URLUtils;

public class NormalizeUrl
{
    private static final String QP_SEP_A = "&";
    private static final String NAME_VALUE_SEPARATOR = "=";

    public static class Options
    {
        /**
         * Adds {@link Options#defaultProtocol} to the URL if it's protocol-relative.
         * Default is true.
         *
         * Example:
         *    Before: "//example.org"
         *    After: "http://example.org"
         */
        public boolean normalizeProtocol = true;
        public String defaultProtocol = "http";
        /**
         * Removes "www." from the URL.
         * Default is true.
         *
         * Example:
         *    Before: "http://www.example.org"
         *    After: "http://example.org"
         */
        public boolean removeWWW = true;
        /**
         * Removes query parameters that match any of the provided strings or regular expressions.
         * Default is "utm_\w+"
         *
         * Example:
         *    Before: "http://example.org?foo=bar&utm_medium=test"
         *    After: "http://example.org?foo=bar"
         */
        public String[] removeQueryParameters = new String[]{"utm_\\w+"};
        /**
         * Removes trailing slash.
         * Default is true.
         *
         * Example:
         *    Before: "http://example.org/"
         *    After: "http://example.org"
         */
        public boolean removeTrailingSlash = true;
        /**
         * Removes the default directory index file from a path that
         * matches any of the provided strings or regular expressions.
         * Default is empty.
         *
         * Example:
         *    Before: "http://example.org/index.html"
         *    After: "http://example.org"
         */
        public String[] removeDirectoryIndex = new String[]{};
        /**
         * Remove the authentication part of a URL,
         * see https://en.wikipedia.org/wiki/Basic_access_authentication
         * Default is true.
         *
         * Example:
         *    Before: "http://user:password@example.org"
         *    After: "http://example.org"
         */
        public boolean removeAuthentication = true;
        /**
         * Sorts the query parameters alphabetically by key.
         * Default is true.
         *
         * Example:
         *    Before: "http://example.org?b=two&a=one&c=three"
         *    After: "http://example.org?a=one&b=two&c=three"
         */
        public boolean sortQueryParameters = true;
        /**
         * Removes hash from the URL.
         * Default is false.
         *
         * Example:
         *    Before: "http://example.org/index.html#test"
         *    After: "http://example.org/index.html"
         */
        public boolean removeHash = false;
        /**
         * Removes HTTP(S) protocol from an URL.
         * Default is false.
         *
         * Example:
         *    Before: "http://example.org"
         *    After: "example.org"
         */
        public boolean removeProtocol = false;
        /**
         * Normalizes "https" URLs to "http".
         * Default is false.
         *
         * Example:
         *    Before: "https://example.org"
         *    After: "http://example.org"
         */
        public boolean forceHttp = false;
        /**
         * Normalizes "http" URLs to "https".
         * This option can't be used with {@link Options#forceHttp} option at the same time.
         * Default is false.
         *
         * Example:
         *    Before: "http://example.org"
         *    After: "https://example.org"
         */
        public boolean forceHttps = false;
        /**
         * Decode the percent-decoded symbols and IDN in the URL to Unicode symbols.
         * Default is true.
         *
         * Example:
         *    Before: "https://example.org/?foo=bar*%7C%3C%3E%3A%22"
         *    After: "http://example.org/?foo=bar*|<>:""
         */
        public boolean decode = true;
    }

    /**
     * More about URL normalization: https://en.wikipedia.org/wiki/URL_normalization
     *
     * @param url URL
     * @return normalized URL
     */

    public static String normalize(String url) throws NormalizeUrlException
    {
        return normalize(url, null);
    }

    /**
     * More about URL normalization: https://en.wikipedia.org/wiki/URL_normalization
     *
     * @param url URL
     * @param options additional options for normalization
     * @return normalized URL
     */

    public static String normalize(String url, Options options) throws NormalizeUrlException
    {
        String normalizedUrl;
        try {
            normalizedUrl = doNormalize(url, options);

        } catch (GalimatiasParseException e) {
            throw new NormalizeUrlException("Cannot normalize URL", e);
        }

        return normalizedUrl;
    }

    private static String doNormalize(String url, Options options) throws GalimatiasParseException
    {
        if (url == null || url.isEmpty())
            return url;

        if (options == null)
            options = new Options();
        if (options.forceHttp && options.forceHttps)
            throw new IllegalStateException("The 'forceHttp' and 'forceHttps' options cannot be used together");

        url = url.trim();

        boolean hasRelativeProtocol = url.startsWith("//");
        boolean isRelativeUrl = !hasRelativeProtocol && url.matches("^.*/");
        if (!isRelativeUrl)
            url = url.replaceFirst("^(?!(?:\\w+:)?//)|^//", options.defaultProtocol + "://");

        URL urlObj = URL.parse(url);
        String protocol, protocolData, hash, userInfo, path, host, queryStr;
        int port;
        Map<String, List<String>> query;

        protocol = urlObj.scheme();
        protocolData = urlObj.schemeData();
        hash = urlObj.fragment();
        userInfo = urlObj.userInfo();
        path = urlObj.path();
        queryStr = urlObj.query();
        if (urlObj.host() != null) {
            if (options.decode)
                host = urlObj.host().toHumanString();
            else
                host = urlObj.host().toString();
        } else {
            host = null;
        }
        port = urlObj.port();
        /* Ignore default ports */
        if (port == urlObj.defaultPort())
            port = -1;

        if (options.decode) {
            protocolData = percentDecode(protocolData);
            hash = percentDecode(hash);
            userInfo = percentDecode(userInfo);
            path = percentDecode(path);
            queryStr = percentDecode(queryStr);
        }
        try {
            query = parseQuery(queryStr);

        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Cannot normalize URL", e);
        }

        if (options.forceHttp && protocol.equals("https"))
            protocol = "http";
        if (options.forceHttps && protocol.equals("http"))
            protocol = "https";

        if (options.removeAuthentication)
            userInfo = null;

        if (options.removeHash)
            hash = null;

        if (host != null) {
            /* Remove trailing dot */
            host = host.replaceFirst("\\.$", "");

            if (options.removeWWW && host.matches("www\\.([a-z\\-\\d]{2,63})\\.([a-z.]{2,5})$")) {
                /*
                 * Each label should be max 63 at length (min: 2).
                 * The extension should be max 5 at length (min: 2).
                 * See: https://en.wikipedia.org/wiki/Hostname#Restrictions_on_valid_host_names
                 */
                host = host.replaceFirst("^www\\.", "");
            }
        }

        if (path != null) {
            /* Remove duplicate slashes if not preceded by a protocol */
            path = path.replaceAll("(?<!:)/{2,}", "/");

            if (options.removeDirectoryIndex.length > 0) {
                String[] pathComponents = path.split("/");
                if (pathComponents.length > 0) {
                    String lastComponent = pathComponents[pathComponents.length - 1];
                    if (isMatch(lastComponent, options.removeDirectoryIndex))
                        path = '/' + join("/", Arrays.copyOfRange(pathComponents, 1, pathComponents.length - 1));
                    if (!path.endsWith("/"))
                        path += '/';
                }
            }
            if (options.removeTrailingSlash)
                path = path.replaceFirst("/$", "");
        }

        if (!query.isEmpty()) {
            if (options.removeQueryParameters.length > 0) {
                Iterator<String> it = query.keySet().iterator();
                while (it.hasNext()) {
                    String key = it.next();
                    if (isMatch(key, options.removeQueryParameters))
                        it.remove();
                }
            }
            if (options.sortQueryParameters)
                query = new TreeMap<>(query);
        }

        url = urlToString(protocol, protocolData, userInfo,
                host, port, path, mapToQuery(query),
                hash, urlObj.isHierarchical(),
                urlObj.host() instanceof IPv6Address);

        /* Restore relative protocol, if applicable */
        if (hasRelativeProtocol && !options.normalizeProtocol)
            url = url.replaceFirst("^(?:https?:)?//", "//");

        /* Remove http/https */
        if (options.removeProtocol)
            url = url.replaceFirst("^(?:https?:)?//", "");

        return url;
    }

    private static boolean isMatch(String s, String[] filterList)
    {
        for (String filter : filterList) {
            if (filter == null)
                continue;

            if (s.matches(filter))
                return true;
        }

        return false;
    }

    private static String join(CharSequence delimiter, Object[] tokens)
    {
        int length = tokens.length;
        if (length == 0)
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append(tokens[0]);
        for (int i = 1; i < length; i++) {
            sb.append(delimiter);
            sb.append(tokens[i]);
        }

        return sb.toString();
    }

    private static Map<String, List<String>> parseQuery(String query) throws UnsupportedEncodingException
    {
        Map<String, List<String>> queryPairs = new LinkedHashMap<>();
        if (query == null)
            return queryPairs;

        String[] pairs = query.split(QP_SEP_A);

        for (String pair : pairs) {
            int idx = pair.indexOf(NAME_VALUE_SEPARATOR);
            String key = (idx > 0 ? pair.substring(0, idx) : pair);
            if (!queryPairs.containsKey(key))
                queryPairs.put(key, new LinkedList<>());

            String value = (idx > 0 && pair.length() > idx + 1 ?
                    pair.substring(idx + 1) :
                    null);
            queryPairs.get(key).add(value);
        }

        return queryPairs;
    }

    private static String mapToQuery(Map<String, List<String>> query)
    {
        if (query.isEmpty())
            return null;

        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, List<String>> parameter : query.entrySet()) {
            for (String value : parameter.getValue()) {
                if (i > 0)
                    sb.append(QP_SEP_A);
                sb.append(parameter.getKey());
                if (value != null && !value.isEmpty()) {
                    sb.append(NAME_VALUE_SEPARATOR);
                    sb.append(value);
                }
                i++;
            }
        }

        return sb.toString();
    }

    private static String percentDecode(String s)
    {
        return (s == null ? null : URLUtils.percentDecode(s));
    }

    private static String urlToString(String protocol, String protocolData,
                                      String userInfo, String host, int port, String path,
                                      String query, String hash, boolean isHierarchical,
                                      boolean isHostIPv6)
    {
        StringBuilder output = new StringBuilder();

        output.append(protocol).append(':');

        if (isHierarchical) {
            output.append("//");
            if (userInfo != null && !userInfo.isEmpty())
                output.append(userInfo).append('@');

            if (host != null) {
                if (isHostIPv6)
                    output.append('[').append(host).append(']');
                else
                    output.append(host);
            }
            if (port != -1)
                output.append(':').append(port);
            if (path != null && !path.isEmpty())
                output.append(path);
            else if (query != null && !query.isEmpty() || hash != null && !hash.isEmpty())
                /* Separate by slash if has query or hash and path is empty */
                output.append("/");

        } else {
            output.append(protocolData);
        }

        if (query != null && !query.isEmpty())
            output.append('?').append(query);

        if (hash != null && !hash.isEmpty())
            output.append('#').append(hash);

        return output.toString();
    }
}
