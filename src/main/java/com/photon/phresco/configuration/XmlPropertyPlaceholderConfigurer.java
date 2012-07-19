package com.photon.phresco.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;

import org.springframework.core.io.Resource;
import org.springframework.util.DefaultPropertiesPersister;
import org.springframework.util.PropertiesPersister;
import org.springframework.util.StringValueResolver;


public class XmlPropertyPlaceholderConfigurer extends
		PropertyPlaceholderConfigurer {
	
	private static final String serverEnvironment = "SERVER_ENVIRONMENT";
	private PropertiesPersister propertiesPersister = new DefaultPropertiesPersister();
	private Resource[] locations;
	private Resource[] configurationTypes;
	private String fileEncoding;
	private boolean ignoreResourceNotFound = false;
	private String nullValue;
	private String beanName;
	private BeanFactory beanFactory;
	
	public void setLocation(Resource location) {
		this.locations = new Resource[] {location};
	}
	
	public void setLocations(Resource[] locations) {
		this.locations = locations;
	}

	public void getConfigurationTypes(Resource configurationTypes) {
		this.configurationTypes = new Resource[] {configurationTypes};
	}

	public void setConfigurationTypes(Resource[] configurationTypes) {
		this.configurationTypes = configurationTypes;
	}

	@Override
	protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess, Properties props)
			throws BeansException {

		org.springframework.util.StringValueResolver valueResolver = new PlaceholderResolvingStringValueResolver(props);
		org.springframework.beans.factory.config.BeanDefinitionVisitor visitor = new org.springframework.beans.factory.config.BeanDefinitionVisitor(valueResolver);

		String[] beanNames = beanFactoryToProcess.getBeanDefinitionNames();
		for (int i = 0; i < beanNames.length; i++) {
			// Check that we're not parsing our own bean definition,
			// to avoid failing on unresolvable placeholders in properties file locations.
			if (!(beanNames[i].equals(this.beanName) && beanFactoryToProcess.equals(this.beanFactory))) {
				BeanDefinition bd = beanFactoryToProcess.getBeanDefinition(beanNames[i]);
				try {
					visitor.visitBeanDefinition(bd);
				}
				catch (BeanDefinitionStoreException ex) {
					throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanNames[i], ex.getMessage());
				}
			}
		}

		// New in Spring 2.5: resolve placeholders in alias target names and aliases as well.
		beanFactoryToProcess.resolveAliases(valueResolver);
	}
	
	/**
	 * BeanDefinitionVisitor that resolves placeholders in String values,
	 * delegating to the <code>parseStringValue</code> method of the
	 * containing class.
	 */
	private class PlaceholderResolvingStringValueResolver implements StringValueResolver {

		private final Properties props;

		public PlaceholderResolvingStringValueResolver(Properties props) {
			this.props = props;
		}

		public String resolveStringValue(String strVal) throws BeansException {
			String value = parseStringValue(strVal, this.props, new HashSet());
			return (value.equals(nullValue) ? null : value);
		}
	}

	@Override
	public void postProcessBeanFactory(
			ConfigurableListableBeanFactory beanFactory) throws BeansException{
		if (this.locations != null) {
			Properties mergedProps = new Properties();
			for (int i = 0; i < this.locations.length; i++) {
				Resource location = this.locations[i];
				InputStream in = null;
				try {
					in = location.getInputStream();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				try {
					if (location.getFilename().endsWith(XML_FILE_EXTENSION)) {
						// this.getClass().getClassLoader().getResourceAsStream(getLocation());
						ConfigReader reader = new ConfigReader(in);
						String envName = System.getProperty(serverEnvironment);
						if (envName.isEmpty()) {
							envName = reader.getDefaultEnvName();
						}
						List<Configuration> configByEnv = reader.getConfigByEnv(envName);
						for (Configuration config : configByEnv) {
							if (this.configurationTypes != null) {
								for (i = 0; i < this.configurationTypes.length; i++) {
									Resource configurationTypes = this.configurationTypes[i];
									String configTypes = configurationTypes.getFilename();
									
									String[] splitStrings = configTypes.split(",");
									List<String> allowedTokens = Arrays.asList(splitStrings);
									

									if (allowedTokens.contains(config.getType())) {
										mergedProps = config.getProperties();
									}
								}
							}
							// props.list(System.out);
						}
					}else {
						if (this.fileEncoding != null) {
							this.propertiesPersister.load(mergedProps, new InputStreamReader(in, this.fileEncoding));
						}
						else {
							this.propertiesPersister.load(mergedProps, in);
						}
					}
					
				}catch (IOException ex) {
						if (this.ignoreResourceNotFound) {
							if (logger.isWarnEnabled()) {
								logger.warn("Could not load properties from " + location + ": " + ex.getMessage());
							}
						}
						else {
							try {
								throw ex;
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
				}catch(Exception e){
					e.printStackTrace();
				}
				finally {
					if (in != null) {
						try {
							in.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
			// Convert the merged properties, if necessary.
			convertProperties(mergedProps);
			// Let the subclass process the properties.
			processProperties(beanFactory, mergedProps);
		}
	}
}
