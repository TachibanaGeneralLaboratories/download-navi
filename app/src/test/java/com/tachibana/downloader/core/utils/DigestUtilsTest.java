/*
 * Copyright (C) 2019 Tachibana General Laboratories, LLC
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of Download Navi.
 *
 * Download Navi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Download Navi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Download Navi.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tachibana.downloader.core.utils;

import org.junit.Test;

import java.nio.charset.Charset;

import static org.junit.Assert.*;

public class DigestUtilsTest
{
    private String unicodeStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZ /0123456789\n" +
            "abcdefghijklmnopqrstuvwxyz £©µÀÆÖÞßéöÿ\n" +
            "–—‘“”„†•…‰™œŠŸž€ ΑΒΓΔΩαβγδω АБВГДабвгд\n" +
            "∀∂∈ℝ∧∪≡∞ ↑↗↨↻⇣ ┐┼╔╘░►☺♀ ﬁ�⑀₂ἠḂӥẄɐː⍎אԱა";
    private String md5HashUnicode = "cb1cdc92d0167e208f37b5f23516856c";
    private String sha256HashUnicode = "ca8beee2cdc37fbca40d2f35864a60b36612dadb7a436f0518b44b9ec9e121b0";

    private byte[] binary = new byte[]{0x7F, 'E', 'L', 'F'};
    private String md5HashBinary = "d1531b1622de54fe3a0187c3344600e9";
    private String sha256HashBinary = "3bdbb4fe8397cd2b842430b39ccff01a8663c751945ef5e9a09e267fb8b1d359";

    @Test
    public void testMakeSha256Hash()
    {
        assertEquals(sha256HashUnicode, DigestUtils.makeSha256Hash(
                unicodeStr.getBytes(Charset.forName("UTF-8"))));
        assertEquals(sha256HashBinary, DigestUtils.makeSha256Hash(binary));
    }

    @Test
    public void testMakeMd5Hash()
    {
        assertEquals(md5HashUnicode, DigestUtils.makeMd5Hash(
                unicodeStr.getBytes(Charset.forName("UTF-8"))));
        assertEquals(md5HashBinary, DigestUtils.makeMd5Hash(binary));
    }

    @Test
    public void testIsMd5Hash()
    {
        assertTrue(DigestUtils.isMd5Hash(md5HashBinary));
        assertTrue(DigestUtils.isMd5Hash(md5HashUnicode));
        assertFalse(DigestUtils.isMd5Hash(sha256HashBinary));
        assertFalse(DigestUtils.isMd5Hash(sha256HashUnicode));
        assertFalse(DigestUtils.isMd5Hash(""));
        assertFalse(DigestUtils.isMd5Hash(unicodeStr));
    }

    @Test
    public void testIsSha256Hash()
    {
        assertTrue(DigestUtils.isSha256Hash(sha256HashBinary));
        assertTrue(DigestUtils.isSha256Hash(sha256HashUnicode));
        assertFalse(DigestUtils.isSha256Hash(md5HashBinary));
        assertFalse(DigestUtils.isSha256Hash(md5HashUnicode));
        assertFalse(DigestUtils.isSha256Hash(""));
        assertFalse(DigestUtils.isSha256Hash(unicodeStr));
    }
}