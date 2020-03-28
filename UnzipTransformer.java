package com.mule.zip;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.mule.api.MuleMessage;
import org.mule.api.transformer.TransformerException;
import org.mule.config.i18n.MessageFactory;
import org.mule.routing.filters.WildcardFilter;
import org.mule.transformer.AbstractMessageAwareTransformer;
import org.mule.transport.file.FileConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@SuppressWarnings("deprecation")
public class UnzipTransformer extends AbstractMessageAwareTransformer {

	private static final Logger log = LoggerFactory.getLogger(UnzipTransformer.class);

	private WildcardFilter filter = new WildcardFilter("*");

	public UnzipTransformer() {
	    registerSourceType(InputStream.class);
	    registerSourceType(byte[].class);
	    setReturnClass(String.class);	    
	}

	public void setFilenamePattern(String pattern) {
		filter.setPattern(pattern);
	}

	public String getFilenamePattern() {
		return filter.getPattern();
	}
	
	@Override
	public Object transform(MuleMessage message, String encoding) throws TransformerException {
		Object payload = message.getPayload();

		InputStream is = null;
		if (payload instanceof InputStream) {
			is = (InputStream)payload;

		} else if (payload instanceof byte[]) {
			is = new ByteArrayInputStream((byte[]) payload);

		} else {
			throw new RuntimeException("Unknown payload type: " + payload.getClass().getName());
		}
		
		String str = message.getInvocationProperty("unzipOutputFolder");

		Path outDir = Paths.get(str);
		ZipInputStream zis = new ZipInputStream(is);
		ZipEntry entry = null;
		try {
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName();
				System.out.println("  ... "+name);
				if (entry.isDirectory()) {
					log.debug("skip folder " + name);
					continue;
				}
				if (!filter.accept(name)) {
					log.debug("skip file " + name + " did not match filename pattern: " + filter.getPattern());
					continue;
				}

				int lastDirSep = name.lastIndexOf('/');
				if (lastDirSep != -1) {
					log.debug("unzip strips zip-folderpath " + name.substring(0, lastDirSep));
					name = name.substring(lastDirSep + 1);
				}
				if (log.isDebugEnabled()) {
					Object oldname = message.getProperty(FileConnector.PROPERTY_ORIGINAL_FILENAME);
					log.debug("unzip replaces original filename " + oldname + " with " + name);
				}
				message.setProperty(FileConnector.PROPERTY_ORIGINAL_FILENAME, name);
				
				//result.add(new FileInfo(name,new BufferedInputStream(zis)));
				byte[] buffer = new byte[2048];
				
				Path filePath = outDir.resolve(name+"."+message.getInvocationProperty("cid"));
				
				try (FileOutputStream fos = new FileOutputStream(filePath.toFile());
                        BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length)) {

                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        bos.write(buffer, 0, len);
                    }
                }
				
				
			}
		} catch (IOException ioException) {
			throw new TransformerException(MessageFactory.createStaticMessage("Failed to uncompress file."), this, ioException);
		}
		return "SUCCESS";
	}
}