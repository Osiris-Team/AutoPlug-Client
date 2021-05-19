/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.managers;

import java.io.InputStream;

@Deprecated
public class DownloaderSpiget implements IDownloader {

    @Override
    public InputStream getInputStreamFromDownload(String download_url) throws Exception {
        return null;
    }

}
