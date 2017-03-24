package org.nzbhydra.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties
@EnableConfigurationProperties
@Data
public class BaseConfig {

    @Autowired
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private ApplicationEventPublisher applicationEventPublisher;
    private AuthConfig auth;
    private List<Category> categories;
    private List<DownloaderConfig> downloaders;
    private List<IndexerConfig> indexers;
    private MainConfig main;
    private SearchingConfig searching;

    public void replace(BaseConfig newConfig) {
        main = newConfig.getMain();
        categories = newConfig.getCategories();
        indexers = newConfig.getIndexers();

        ConfigChangedEvent configChangedEvent = new ConfigChangedEvent(this, this);
        applicationEventPublisher.publishEvent(configChangedEvent);
    }


}