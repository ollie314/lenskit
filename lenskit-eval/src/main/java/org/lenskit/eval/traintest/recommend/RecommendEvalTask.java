/*
 * LensKit, an open source recommender systems toolkit.
 * Copyright 2010-2016 LensKit Contributors.  See CONTRIBUTORS.md.
 * Work on LensKit has been funded by the National Science Foundation under
 * grants IIS 05-34939, 08-08692, 08-12148, and 10-17697.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.lenskit.eval.traintest.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.grouplens.grapht.util.ClassLoaders;
import org.grouplens.lenskit.util.io.CompressionMode;
import org.lenskit.api.ItemRecommender;
import org.lenskit.api.Recommender;
import org.lenskit.api.Result;
import org.lenskit.api.ResultList;
import org.lenskit.eval.traintest.*;
import org.lenskit.eval.traintest.metrics.Metric;
import org.lenskit.eval.traintest.metrics.MetricLoaderHelper;
import org.lenskit.eval.traintest.metrics.MetricResult;
import org.lenskit.eval.traintest.predict.PredictEvalTask;
import org.lenskit.util.collections.LongUtils;
import org.lenskit.util.table.TableLayout;
import org.lenskit.util.table.TableLayoutBuilder;
import org.lenskit.util.table.writer.CSVWriter;
import org.lenskit.util.table.writer.TableWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * An eval task that attempts to recommend items for a test user.
 */
public class RecommendEvalTask implements EvalTask {
    private static final Logger logger = LoggerFactory.getLogger(RecommendEvalTask.class);
    private static final TopNMetric<?>[] DEFAULT_METRICS = {
            new TopNLengthMetric(),
            new TopNNDCGMetric()
    };

    private Path outputFile;
    private String labelPrefix;
    private int listSize = -1;
    private List<TopNMetric<?>> topNMetrics = Lists.newArrayList(DEFAULT_METRICS);
    private volatile ItemSelector candidateSelector = ItemSelector.allItems();
    private volatile ItemSelector excludeSelector = ItemSelector.userTrainItems();

    private ExperimentOutputLayout experimentOutputLayout;
    private TableWriter outputTable;

    /**
     * Create a new recommend eval task.
     */
    public RecommendEvalTask() {}

    /**
     * Parse a recommend task from JSON.
     * @param json The JSON data.
     * @param base The base URI (for resolving relative paths).
     * @return The task.
     * @throws IOException If there is an I/O error.
     */
    public static RecommendEvalTask fromJSON(JsonNode json, URI base) throws IOException {
        RecommendEvalTask task = new RecommendEvalTask();

        String outFile = json.path("output_file").asText(null);
        if (outFile != null) {
            task.setOutputFile(Paths.get(base.resolve(outFile)));
        }

        task.setLabelPrefix(json.path("label_prefix").asText(null));
        task.setListSize(json.path("list_size").asInt(-1));

        String sel = json.path("candidates").asText(null);
        if (sel != null) {
            task.setCandidateSelector(ItemSelector.compileSelector(sel));
        }
        sel = json.path("exclude").asText(null);
        if (sel != null) {
            task.setExcludeSelector(ItemSelector.compileSelector(sel));
        }

        JsonNode metrics = json.get("metrics");
        if (metrics != null && !metrics.isNull()) {
            task.topNMetrics.clear();
            MetricLoaderHelper mlh = new MetricLoaderHelper(ClassLoaders.inferDefault(PredictEvalTask.class),
                                                            "topn-metrics");
            for (JsonNode mn: metrics) {
                TopNMetric<?> metric = mlh.createMetric(TopNMetric.class, mn);
                if (metric != null) {
                    task.addMetric(metric);
                } else {
                    throw new RuntimeException("cannot build metric for " + mn.toString());
                }
            }
        }

        return task;
    }

    /**
     * Get the output file for writing predictions.
     * @return The output file, or {@code null} if no file is configured.
     */
    public Path getOutputFile() {
        return outputFile;
    }

    /**
     * Set the output file for predictions.
     * @param file The output file for writing predictions. Will get a CSV file.
     */
    public void setOutputFile(Path file) {
        outputFile = file;
    }

    /**
     * Get the prefix applied to column labels.
     * @return The column label prefix.
     */
    public String getLabelPrefix() {
        return labelPrefix;
    }

    /**
     * Set the prefix applied to column labels.  If provided, it will be prepended to column labels from this task,
     * along with a ".".
     * @param prefix The label prefix.
     */
    public void setLabelPrefix(String prefix) {
        labelPrefix = prefix;
    }

    /**
     * Get the list size to use.
     * @return The number of items to recommend per user.
     */
    public int getListSize() {
        return listSize;
    }

    /**
     * Set the list size to use.
     * @param n The number of items to recommend per user.
     */
    public void setListSize(int n) {
        listSize = n;
    }

    /**
     * Get the active candidate selector.
     * @return The candidate selector to use.
     */
    public ItemSelector getCandidateSelector() {
        return candidateSelector;
    }

    /**
     * Set the candidate selector.
     * @param sel The candidate selector.
     */
    public void setCandidateSelector(ItemSelector sel) {
        candidateSelector = sel;
    }

    /**
     * Get the active exclude selector.
     * @return The exclude selector to use.
     */
    public ItemSelector getExcludeSelector() {
        return excludeSelector;
    }

    /**
     * Set the exclude selector.
     * @param sel The exclude selector.
     */
    public void setExcludeSelector(ItemSelector sel) {
        excludeSelector = sel;
    }

    /**
     * Get the list of prediction metrics.
     * @return The list of prediction metrics.  This list is live, not copied, so it can be modified or cleared.
     */
    public List<TopNMetric<?>> getTopNMetrics() {
        return topNMetrics;
    }

    /**
     * Get the list of all metrics.
     * @return A list containing all metrics used by this task.
     */
    public List<Metric<?>> getAllMetrics() {
        ImmutableList.Builder<Metric<?>> metrics = ImmutableList.builder();
        metrics.addAll(topNMetrics);
        return metrics.build();
    }

    /**
     * Add a prediction metric.
     * @param metric The metric to add.
     */
    public void addMetric(TopNMetric<?> metric) {
        topNMetrics.add(metric);
    }

    @Override
    public Set<Class<?>> getRequiredRoots() {
        return FluentIterable.from(getAllMetrics())
                             .transformAndConcat(new Function<Metric<?>, Iterable<Class<?>>>() {
                                 @Nullable
                                 @Override
                                 public Iterable<Class<?>> apply(Metric<?> input) {
                                     return input.getRequiredRoots();
                                 }
                             }).toSet();
    }

    @Override
    public List<String> getGlobalColumns() {
        ImmutableList.Builder<String> columns = ImmutableList.builder();
        for (Metric<?> m: getAllMetrics()) {
            for (String label: m.getAggregateColumnLabels()) {
                columns.add(prefixColumn(label));
            }
        }
        return columns.build();
    }

    @Override
    public List<String> getUserColumns() {
        ImmutableList.Builder<String> columns = ImmutableList.builder();
        for (TopNMetric<?> pm: getTopNMetrics()) {
            for (String label: pm.getColumnLabels()) {
                columns.add(prefixColumn(label));
            }
        }
        return columns.build();
    }

    private String prefixColumn(String input) {
        String pfx = getLabelPrefix();
        if (pfx == null) {
            return input;
        } else {
            return pfx + "." + input;
        }
    }

    @Override
    public void start(ExperimentOutputLayout outputLayout) {
        experimentOutputLayout = outputLayout;
        Path outFile = getOutputFile();
        if (outFile == null) {
            return;
        }

        TableLayoutBuilder tlb = TableLayoutBuilder.copy(outputLayout.getConditionLayout());
        TableLayout layout = tlb.addColumn("User")
                                .addColumn("Rank")
                                .addColumn("Item")
                                .addColumn("Score")
                                .build();
        try {
            logger.info("writing recommendations to {}", outFile);
            outputTable = CSVWriter.open(outFile.toFile(), layout, CompressionMode.AUTO);
        } catch (IOException e) {
            throw new EvaluationException("error opening prediction output file", e);
        }
    }

    @Override
    public void finish() {
        experimentOutputLayout = null;
        if (outputTable != null) {
            try {
                outputTable.close();
                outputTable = null;
            } catch (IOException e) {
                throw new EvaluationException("error closing prediction output file", e);
            }
        }
    }

    @Override
    public ConditionEvaluator createConditionEvaluator(AlgorithmInstance algorithm, DataSet dataSet, Recommender rec) {
        Preconditions.checkState(experimentOutputLayout != null, "experiment not started");
        TableWriter recTable = experimentOutputLayout.prefixTable(outputTable, dataSet, algorithm);
        LongSet items = dataSet.getAllItems();
        ItemRecommender irec = rec.getItemRecommender();
        if (irec == null) {
            logger.warn("algorithm {} has no item recommender", algorithm);
            return null;
        }

        // we need details to write recommendation output
        boolean useDetails = recTable != null;
        List<MetricContext<?>> contexts = new ArrayList<>(topNMetrics.size());
        for (TopNMetric<?> metric: topNMetrics) {
            logger.debug("setting up metric {}", metric);
            MetricContext<?> mc = MetricContext.create(metric, algorithm, dataSet, rec);
            contexts.add(mc);
            // does this metric require details?
            useDetails |= mc.usesDetails();
        }

        return new TopNConditionEvaluator(recTable, rec, irec, contexts, items, useDetails);
    }

    static class MetricContext<X> {
        final TopNMetric<X> metric;
        final X context;

        public MetricContext(TopNMetric<X> m, X ctx) {
            metric = m;
            context = ctx;
        }

        public boolean usesDetails() {
            return !(metric instanceof ListOnlyTopNMetric);
        }

        @Nonnull
        public MetricResult measureUser(TestUser user, int n, ResultList recommendations) {
            return metric.measureUser(user, n, recommendations, context);
        }

        @Nonnull
        public MetricResult measureUser(TestUser user, int n, LongList recommendations) {
            return ((ListOnlyTopNMetric<X>) metric).measureUser(user, n, recommendations, context);
        }

        @Nonnull
        public MetricResult getAggregateMeasurements() {
            return metric.getAggregateMeasurements(context);
        }

        /**
         * Create a new metric context. Indirected through this method to help the type checker.
         */
        public static <X> MetricContext<X> create(TopNMetric<X> metric, AlgorithmInstance algorithm, DataSet dataSet, Recommender rec) {
            X ctx = metric.createContext(algorithm, dataSet, rec);
            return new MetricContext<>(metric, ctx);
        }
    }

    class TopNConditionEvaluator implements ConditionEvaluator {
        private final TableWriter writer;
        private final Recommender recommender;
        private final ItemRecommender itemRecommender;
        private final List<MetricContext<?>> predictMetricContexts;
        private final LongSet allItems;
        private final boolean useDetails;

        public TopNConditionEvaluator(TableWriter tw, Recommender rec, ItemRecommender irec,
                                      List<MetricContext<?>> mcs, LongSet items, boolean details) {
            writer = tw;
            recommender = rec;
            itemRecommender = irec;
            predictMetricContexts = mcs;
            allItems = items;
            useDetails = details;
        }

        @Nonnull
        @Override
        public Map<String, Object> measureUser(TestUser testUser) {
            LongSet candidates = getCandidateSelector().selectItems(allItems, recommender, testUser);
            LongSet excludes = getExcludeSelector().selectItems(allItems, recommender, testUser);
            int n = getListSize();
            ResultList results = null;
            LongList items = null;
            if (useDetails) {
                results = itemRecommender.recommendWithDetails(testUser.getUserId(), n,
                                                               candidates, excludes);
            } else {
                // no one needs details, save time collecting them
                items = LongUtils.asLongList(itemRecommender.recommend(testUser.getUserId(), n,
                                                                       candidates, excludes));
            }

            // Measure the user results
            Map<String,Object> row = new HashMap<>();
            for (MetricContext<?> mc: predictMetricContexts) {
                MetricResult res;
                if (useDetails) {
                    res = mc.measureUser(testUser, n, results);
                } else {
                    res = mc.measureUser(testUser, n, items);
                }
                row.putAll(res.withPrefix(getLabelPrefix())
                              .getValues());
            }

            // Write all attempted predictions
            if (writer != null) {
                assert results != null; // we use details when writer is nonnull
                int rank = 0;
                for (Result rec : results) {
                    try {
                        rank += 1;
                        writer.writeRow(testUser.getUserId(), rank, rec.getId(), rec.getScore());
                    } catch (IOException ex) {
                        throw new EvaluationException("error writing prediction row", ex);
                    }
                }
            }

            return row;
        }

        @Nonnull
        @Override
        public Map<String, Object> finish() {
            Map<String,Object> results = new HashMap<>();
            for (MetricContext<?> mc: predictMetricContexts) {
                logger.debug("finishing metric {}", mc.metric);
                results.putAll(mc.getAggregateMeasurements()
                                 .withPrefix(getLabelPrefix())
                                 .getValues());
            }
            return results;
        }
    }
}
