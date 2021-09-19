/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.managers;

import java.io.InputStream;

@Deprecated
public interface IDownloader {

    /**
     * This is used to get the InputStream of an online file.
     * The download_url must point directly to that file.
     *
     * @return null if the download wasn't successful.
     */
    InputStream getInputStreamFromDownload(String download_url) throws Exception;

}
