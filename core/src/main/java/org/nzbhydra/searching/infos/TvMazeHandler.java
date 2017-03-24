package org.nzbhydra.searching.infos;

import com.google.common.base.MoreObjects;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TvMazeHandler {

    private static final Logger logger = LoggerFactory.getLogger(TvMazeHandler.class);

    @Autowired
    protected RestTemplate restTemplate;


    public TvMazeSearchResult getInfos(String id, InfoProvider.IdType idType) throws InfoProviderException {
        if (idType == InfoProvider.IdType.TVTITLE) {
            return fromTitle(id);
        }
        logger.info("Searching TVMaze for show with {} {}", idType, id);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("http://api.tvmaze.com/");
        if (idType == InfoProvider.IdType.TVRAGE) {
            builder = builder.pathSegment("lookup", "shows").queryParam("tvrage", id);
        } else if (idType == InfoProvider.IdType.TVDB) {
            builder = builder.pathSegment("lookup", "shows").queryParam("thetvdb", id);
        } else if (idType == InfoProvider.IdType.TVMAZE) {
            builder = builder.path("shows").path(id);
        } else {
            throw new InfoProviderException("Unable to handle " + idType);
        }

        ResponseEntity<TvmazeShow> showLookupResponse = restTemplate.getForEntity(builder.build().encode().toUri(), TvmazeShow.class);

        if (!showLookupResponse.getStatusCode().is2xxSuccessful()) {
            throw new InfoProviderException("TVMaze lookup returned wrong status: " + showLookupResponse.getStatusCode());
        }
        TvmazeShow show = showLookupResponse.getBody();
        logger.info("TVMaze found show {}", show);
        return getSearchResultFromShow(show);
    }

    private TvMazeSearchResult fromTitle(String title) throws InfoProviderException {
        logger.info("Searching TVMaze for show with title '{}", title);
        List<TvmazeShowSearch> shows = searchByTitle(title);
        TvmazeShow show = shows.get(0).getShow();
        logger.info("TVMaze found {} shows for title '{}'. Using first show {} with a confidence level of {}", shows.size(), title, show, shows.get(0).getScore());
        return getSearchResultFromShow(show);
    }

    private TvMazeSearchResult getSearchResultFromShow(TvmazeShow show) {
        Instant premiered = Instant.parse(show.premiered);
        Integer year = premiered != null ? LocalDateTime.ofInstant(premiered, ZoneId.systemDefault()).get(ChronoField.YEAR) : null;
        return new TvMazeSearchResult(String.valueOf(show.getId()), show.getExternals().getTvrage(), show.getExternals().getThetvdb(), show.getName(), year, show.getMediumPosterUrl());
    }

    public List<TvMazeSearchResult> search(String title) throws InfoProviderException {
        logger.info("Searching TVMaze for shows with title '{}", title);
        List<TvmazeShowSearch> shows = searchByTitle(title);
        logger.info("TVMaze found {} shows for title '{}'", shows.size(), title);
        return shows.stream().map(showSearch -> getSearchResultFromShow(showSearch.getShow())).collect(Collectors.toList());
    }

    private List<TvmazeShowSearch> searchByTitle(String title) throws InfoProviderException {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("http://api.tvmaze.com/search/shows").queryParam("q", title);
        ParameterizedTypeReference<List<TvmazeShowSearch>> typeRef = new ParameterizedTypeReference<List<TvmazeShowSearch>>() {
        };
        ResponseEntity<List<TvmazeShowSearch>> lookupResponse = restTemplate.exchange(builder.build().encode().toUri(), HttpMethod.GET, null, typeRef);
        if (!lookupResponse.getStatusCode().is2xxSuccessful()) {
            throw new InfoProviderException("TVMaze lookup returned wrong status: " + lookupResponse.getStatusCode());
        }
        List<TvmazeShowSearch> shows = lookupResponse.getBody();
        if (shows.isEmpty()) {
            throw new InfoProviderException("TVMaze found no series with title " + title);
        }
        return shows;
    }


    @Data
    private static class TvmazeShowSearch { //Without static deserialization fails
        private Integer score;
        private TvmazeShow show;
    }

    @Data
    private static class TvmazeShow { //Without static deserialization fails
        private Integer id;
        private String name;
        private String premiered;
        private TvmazeImage image;
        private TvmazeExternals externals;

        public String getMediumPosterUrl() {
            return getImage() != null ? getImage().getMedium() : null;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", id)
                    .add("name", name)
                    .add("image", image)
                    .add("externals", externals)
                    .toString();
        }
    }

    @Data
    private static class TvmazeExternals { //Without static deserialization fails
        private String tvrage;
        private String thetvdb;
        private String imdb;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("tvrage", tvrage)
                    .add("thetvdb", thetvdb)
                    .add("imdb", imdb)
                    .toString();
        }
    }

    @Data
    private static class TvmazeImage { //Without static deserialization fails
        private String medium;
        private String original;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("medium", medium)
                    .add("original", original)
                    .toString();
        }
    }
}