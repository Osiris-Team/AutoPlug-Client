/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.managers;

import org.jetbrains.annotations.Nullable;

import java.io.InputStream;

@Deprecated
public class DownloaderSpiget implements IDownloader {

    @Nullable
    @Override
    public InputStream getInputStreamFromDownload(String download_url) throws Exception {
        return null;
    }

}
