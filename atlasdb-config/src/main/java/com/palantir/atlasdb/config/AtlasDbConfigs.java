/**
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.config;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Strings;

import io.dropwizard.jackson.DiscoverableSubtypeResolver;

public final class AtlasDbConfigs {

    public static final String ATLASDB_CONFIG_ROOT = "/atlasdb";

    private AtlasDbConfigs() {
        // uninstantiable
    }

    public static AtlasDbConfig load(File configFile) throws IOException {
        return load(configFile, ATLASDB_CONFIG_ROOT);
    }

    public static AtlasDbConfig load(File configFile, String configRoot) throws IOException {
        ObjectMapper configMapper = new ObjectMapper(new YAMLFactory());
        configMapper.setSubtypeResolver(new DiscoverableSubtypeResolver());
        JsonNode rootNode = getConfigNode(configMapper, configFile, configRoot);
        return configMapper.treeToValue(rootNode, AtlasDbConfig.class);
    }

    private static JsonNode getConfigNode(ObjectMapper configMapper, File configFile, String configPath) throws IOException {
        JsonNode node = configMapper.readTree(configFile);
        if (Strings.isNullOrEmpty(configPath)) {
            return node;
        } else {
            JsonNode root = node.at(JsonPointer.valueOf(configPath));
            if (root.isMissingNode()) {
                throw new IllegalArgumentException("Could not find " + configPath + " in yaml file " + configFile);
            }
            return root;
        }
    }
}
