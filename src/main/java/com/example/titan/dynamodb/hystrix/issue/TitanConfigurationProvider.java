/*
 * [y] hybris Platform
 *
 * Copyright (c) 2000-2014 hybris AG
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of hybris
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with hybris.
 */
package com.example.titan.dynamodb.hystrix.issue;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Iterator;
import java.util.Properties;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thinkaurelius.titan.diskstorage.configuration.backend.CommonsConfiguration;
import com.thinkaurelius.titan.graphdb.configuration.GraphDatabaseConfiguration;

/**
 * Provide configuration for {@code StandardTitanGraph} instance.
 */
public class TitanConfigurationProvider
{
    private static final Logger LOG = LoggerFactory.getLogger(TitanConfigurationProvider.class);
    private static final String STORAGE_HOSTNAME_KEY = "storage.hostname";

    private String propertyFile;
    private String storageHostname;
    private Properties properties;


    public GraphDatabaseConfiguration load() throws ConfigurationException, FileNotFoundException
    {
        final PropertiesConfiguration configuration = new PropertiesConfiguration();

        // load property file if provided
        if (propertyFile != null)
        {
            final URL resource = getClass().getClassLoader().getResource(propertyFile);

            if (null == resource)
            {
                LOG.error("File 'titan.properties' cannot be found.");
                throw new FileNotFoundException("File 'titan.properties' cannot be found.");
            }

            configuration.load(resource);
        }

        configuration.setProperty(STORAGE_HOSTNAME_KEY, storageHostname);

		if(StringUtils.isEmpty(properties.getProperty("storage.dynamodb.client.credentials.class-name")))
		{
			properties.remove("storage.dynamodb.client.credentials.class-name");
			properties.remove("storage.dynamodb.client.credentials.constructor-args");
		}

        if (properties != null)
        {

            properties.stringPropertyNames()
                    .stream()
                    .forEach(prop -> configuration.setProperty(prop, properties.getProperty(prop)));
        }


		LOG.info("Titan configuration: \n" + secureToString(configuration));

        // Warning: calling GraphDatabaseConfiguration constructor results in opening connections to backend storage
        return new GraphDatabaseConfiguration(new CommonsConfiguration(configuration));
    }


	private String secureToString(final Configuration configuration)
    {
        final Iterator keys = configuration.getKeys();
        final StringBuilder result = new StringBuilder();

        while (keys.hasNext())
        {
            final String key = (String) keys.next();
            final Object value = configuration.getProperty(key);
            result.append(key).append("=");

            if (key.contains("password"))
            {
                result.append("*********");
            }
            else
            {
                result.append(value);
            }

            if (keys.hasNext())
            {
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Set or overwrite storage hostname
     *
     * @param storageHostname
     */
    public void setStorageHostname(final String storageHostname)
    {
        this.storageHostname = storageHostname;
    }

    /**
     * Titan property file path. The path must point to classpath resource because it is looked up using java.lang
     * .ClassLoader#getResource(java.lang.String).
     */
    public void setPropertyFile(final String propertyFile)
    {
        this.propertyFile = propertyFile;
    }

    /**
     * Sets property overrides. The properties override any settings from <code>propertyFile</code>.
     */
    public void setProperties(final Properties props)
    {
        this.properties = props;
    }
}
