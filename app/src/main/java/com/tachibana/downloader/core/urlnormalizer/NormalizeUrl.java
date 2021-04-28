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

package com.tachibana.downloader.core.urlnormalizer;

import com.anthonynsimon.url.URL;
import com.anthonynsimon.url.exceptions.MalformedURLException;
import com.tachibana.downloader.core.exception.NormalizeUrlException;

import java.net.IDN;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class NormalizeUrl
{
    private static final HashMap<String, Integer> DEFAULT_PORT_LIST = new HashMap<>();
    static {
        DEFAULT_PORT_LIST.put("http", 80);
        DEFAULT_PORT_LIST.put("https", 443);
        DEFAULT_PORT_LIST.put("ftp", 21);
    }

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
         * Decode IDN in the URL to Unicode symbols.
         * Default is true.
         *
         * Example:
         *    Before: "https://xn--xample-hva.com"
         *    After: "https://êxample.com"
         */
        public boolean decodeIDN = true;
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

        } catch (Exception e) {
            throw new NormalizeUrlException("Cannot normalize URL", e);
        }

        return normalizedUrl;
    }

    private static String doNormalize(String url, Options options) throws MalformedURLException
    {
        if (url == null || url.isEmpty())
            return url;

        if (options == null)
            options = new Options();

        url = url.trim();

        boolean hasRelativeProtocol = url.startsWith("//");
        boolean isRelativeUrl = !hasRelativeProtocol && url.matches("^.*/");
        if (!isRelativeUrl)
            url = url.replaceFirst("^(?!(?:\\w+:)?//)|^//", options.defaultProtocol + "://");

        URL urlObj = URL.parse(url);
        String protocol, hash, user, password, path, query, host;

        protocol = urlObj.getScheme();
        hash = urlObj.getFragment();
        user = urlObj.getUsername();
        password = urlObj.getPassword();
        query = urlObj.getQuery();
        path = urlObj.getRawPath();
        if (options.decodeIDN) {
            host = IDN.toUnicode(urlObj.getHost());
        } else {
            host = urlObj.getHost();
        }

        if (host != null) {
            /* Remove trailing dot */
            host = host.replaceFirst("\\.$", "");
            /* Ignore default ports */
            Integer port = DEFAULT_PORT_LIST.get(protocol);
            if (port != null)
                host = host.replaceFirst(":" + port + "$", "");

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
            path = PathResolver.resolve(path, path);
            /* Remove duplicate slashes if not preceded by a protocol */
            path = path.replaceAll("(?<!:)/{2,}", "/");

            if (options.removeTrailingSlash)
                path = path.replaceFirst("/$", "");
        }

        url = urlToString(protocol, user, password, host,
                path, query, hash);

        /* Restore relative protocol, if applicable */
        if (hasRelativeProtocol && !options.normalizeProtocol)
            url = url.replaceFirst("^(?:https?:)?//", "//");

        return url;
    }

    private static String urlToString(String protocol, String user, String password,
                                      String host, String path,
                                      String query, String hash)
    {
        StringBuilder output = new StringBuilder();

        output.append(protocol).append(':');

        output.append("//");
        if (user != null && !user.isEmpty())
            output.append(makeUserInfo(user, password)).append('@');

        if (host != null)
            output.append(host);
        if (path != null && !path.isEmpty())
            output.append(path);
        else if (query != null && !query.isEmpty() || hash != null && !hash.isEmpty())
            /* Separate by slash if has query or hash and path is empty */
            output.append("/");

        if (query != null && !query.isEmpty())
            output.append('?').append(query);

        if (hash != null && !hash.isEmpty())
            output.append('#').append(hash);

        return output.toString();
    }

    private static String makeUserInfo(String user, String password)
    {
        if (password == null)
            return user;

        return String.format("%s:%s", user, password);
    }
}
