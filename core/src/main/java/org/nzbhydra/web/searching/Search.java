package org.nzbhydra.web.searching;

import com.google.common.collect.Iterables;
import org.nzbhydra.searching.*;
import org.nzbhydra.searching.searchmodules.Indexer;
import org.nzbhydra.searching.searchrequests.SearchRequest;
import org.nzbhydra.web.searching.mapping.IndexerSearchMetaData;
import org.nzbhydra.web.searching.mapping.SearchResponse;
import org.nzbhydra.web.searching.mapping.SearchResult;
import org.nzbhydra.web.searching.mapping.SearchResult.SearchResultBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;

@RestController
public class Search {

    @Autowired
    private Searcher searcher;
    @Autowired
    private CategoryProvider categoryProvider;

    Random random = new Random();

    @RequestMapping(value = "/internalapi/search", produces = "application/json")
    public SearchResponse search(@RequestParam(value = "query", required = false) String query,
                                 @RequestParam(value = "offset", required = false) Integer offset,
                                 @RequestParam(value = "limit", required = false) Integer limit,
                                 @RequestParam(value = "minsize", required = false) Integer minsize,
                                 @RequestParam(value = "maxsize", required = false) Integer maxsize,
                                 @RequestParam(value = "minage", required = false) Integer minage,
                                 @RequestParam(value = "maxage", required = false) Integer maxage,
                                 @RequestParam(value = "loadAll", required = false) Boolean loadAll,
                                 @RequestParam(value = "category", required = false) String category
    ) {
        SearchRequest searchRequest = new SearchRequest(SearchType.SEARCH, offset, limit);
        searchRequest.setOffset(offset);
        searchRequest.setMinage(minage);
        searchRequest.setMaxage(maxage);
        searchRequest.setMinsize(minsize);
        searchRequest.setMaxsize(maxsize);
        searchRequest.setSearchType(SearchType.SEARCH);
        searchRequest.getInternalData().setLoadAll(loadAll == null ? false : loadAll);
        searchRequest.setCategory(categoryProvider.getByName(category));

        org.nzbhydra.searching.SearchResult searchResult = searcher.search(searchRequest);
        SearchResponse response = new SearchResponse();

        List<SearchResult> transformedSearchResults = transformSearchResults(searchResult);
        response.setSearchResults(transformedSearchResults);


        for (Entry<Indexer, List<IndexerSearchResult>> entry : searchResult.getIndexerSearchResultMap().entrySet()) {
            //For now it's enough to get the data from the last metadata entry (even if multiple were done to get the needed amount of results)
            IndexerSearchResult indexerSearchResult = Iterables.getLast(entry.getValue());
            IndexerSearchMetaData indexerSearchMetaData = new IndexerSearchMetaData();
            indexerSearchMetaData.setDidSearch(true); //TODO
            indexerSearchMetaData.setErrorMessage(indexerSearchResult.getErrorMessage());
            indexerSearchMetaData.setHasMoreResults(indexerSearchResult.isHasMoreResults());
            indexerSearchMetaData.setIndexerName(indexerSearchResult.getIndexer().getName());
            indexerSearchMetaData.setLimit(indexerSearchResult.getLimit());
            indexerSearchMetaData.setNotPickedReason("TODO"); //TODO
            indexerSearchMetaData.setNumberOfAvailableResults(indexerSearchResult.getTotalResults());
            indexerSearchMetaData.setNumberOfResults(indexerSearchResult.getSearchResultItems().size());
            indexerSearchMetaData.setOffset(indexerSearchResult.getOffset());
            indexerSearchMetaData.setResponseTime(indexerSearchResult.getResponseTime());
            indexerSearchMetaData.setTotalResultsKnown(indexerSearchResult.isTotalResultsKnown());
            indexerSearchMetaData.setWasSuccessful(indexerSearchResult.isWasSuccessful());
            response.getIndexerSearchMetaDatas().add(indexerSearchMetaData);

        }
        response.getIndexerSearchMetaDatas().sort(Comparator.comparing(IndexerSearchMetaData::getIndexerName));

        response.setLimit(searchRequest.getLimit().orElse(100)); //TODO: Can this ever be actually null?
        response.setOffset(searchRequest.getOffset().orElse(0)); //TODO: Can this ever be actually null?
        response.setNumberOfAvailableResults(searchResult.getIndexerSearchResultMap().values().stream().mapToInt(x -> Iterables.getLast(x).getTotalResults()).sum()); //TODO
        response.setNumberOfRejectedResults(searchResult.getRejectedReaonsMap().values().stream().mapToInt(x -> x).sum());
        response.setNumberOfResults(transformedSearchResults.size());
        response.setRejectedReasonsMap(new HashMap<>()); //TODO

        return response;
    }

    private List<SearchResult> transformSearchResults(org.nzbhydra.searching.SearchResult searchResult) {
        List<SearchResult> transformedSearchResults = new ArrayList<>();
        List<TreeSet<SearchResultItem>> duplicateGroups = searchResult.getDuplicateDetectionResult().getDuplicateGroups();
        for (TreeSet<SearchResultItem> duplicateGroup : duplicateGroups) {
            int groupResultsIdentifier = random.nextInt();
            for (SearchResultItem item : duplicateGroup) {

                SearchResultBuilder builder = SearchResult.builder()
                        .category("todo")
                        .comments(0) //TODO
                        .details_link(item.getDetails())
                        .downloadType("downloadType") //TODO
                        .files(0) //TODO
                        .grabs(0) //TODO
                        .has_nfo(0) //TODO
                        .hash(groupResultsIdentifier)
                        .indexer(item.getIndexer().getName())
                        .indexerguid(item.getIndexerGuid())
                        .indexerscore(item.getIndexer().getConfig().getScore())
                        .link(item.getLink())
                        .searchResultId(item.getSearchResultId())
                        .size(item.getSize())
                        .title(item.getTitle());
                builder = setSearchResultDateRelatedValues(builder, item);
                transformedSearchResults.add(builder.build());
            }
        }
        return transformedSearchResults;
    }

    private SearchResultBuilder setSearchResultDateRelatedValues(SearchResultBuilder builder, SearchResultItem item) {
        long ageInDays = item.getPubDate().until(Instant.now(), ChronoUnit.DAYS);
        if (ageInDays > 0) {
            builder.age(ageInDays + "d");
        } else {
            long ageInHours = item.getPubDate().until(Instant.now(), ChronoUnit.HOURS);
            builder.age(ageInHours + "d");
        }
        //TODO: Use usenet date if availabke
        builder = builder
                .age_precise(true) //TODO
                .epoch(item.getPubDate().getEpochSecond())
                .pubdate_utc("todo");
        return builder;
    }
}