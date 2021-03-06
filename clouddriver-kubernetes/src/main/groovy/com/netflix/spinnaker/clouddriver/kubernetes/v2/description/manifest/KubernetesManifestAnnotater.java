/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class KubernetesManifestAnnotater {
  private static final String SPINNAKER_ANNOTATION = "spinnaker.io";
  private static final String RELATIONSHIP_ANNOTATION_PREFIX = "relationships." + SPINNAKER_ANNOTATION;
  private static final String ARTIFACT_ANNOTATION_PREFIX = "artifact." + SPINNAKER_ANNOTATION;
  private static final String MONIKER_ANNOTATION_PREFIX = "moniker." + SPINNAKER_ANNOTATION;
  private static final String CACHING_ANNOTATION_PREFIX = "caching." + SPINNAKER_ANNOTATION;
  private static final String LOAD_BALANCERS = RELATIONSHIP_ANNOTATION_PREFIX + "/loadBalancers";
  private static final String SECURITY_GROUPS = RELATIONSHIP_ANNOTATION_PREFIX + "/securityGroups";
  private static final String CLUSTER = MONIKER_ANNOTATION_PREFIX + "/cluster";
  private static final String APPLICATION = MONIKER_ANNOTATION_PREFIX + "/application";
  private static final String STACK = MONIKER_ANNOTATION_PREFIX + "/stack";
  private static final String DETAIL = MONIKER_ANNOTATION_PREFIX + "/detail";
  private static final String TYPE = ARTIFACT_ANNOTATION_PREFIX + "/type";
  private static final String NAME = ARTIFACT_ANNOTATION_PREFIX + "/name";
  private static final String LOCATION = ARTIFACT_ANNOTATION_PREFIX + "/location";
  private static final String VERSION = ARTIFACT_ANNOTATION_PREFIX + "/version";
  private static final String IGNORE_CACHING = CACHING_ANNOTATION_PREFIX + "/ignore";

  private static final String KUBERNETES_ANNOTATION = "kubernetes.io";
  private static final String KUBECTL_ANNOTATION_PREFIX = "kubectl." + KUBERNETES_ANNOTATION;
  private static final String DEPLOYMENT_ANNOTATION_PREFIX = "deployment." + KUBERNETES_ANNOTATION;
  private static final String DEPLOYMENT_REVISION = DEPLOYMENT_ANNOTATION_PREFIX + "/revision";
  private static final String KUBECTL_LAST_APPLIED_CONFIGURATION = KUBECTL_ANNOTATION_PREFIX + "/last-applied-configuration";

  private static ObjectMapper objectMapper = new ObjectMapper();

  private static void storeAnnotation(Map<String, String> annotations, String key, Object value) {
    if (value == null) {
      return;
    }

    try {
      annotations.put(key, objectMapper.writeValueAsString(value));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Illegal annotation value for '" + key + "': " + e);
    }
  }

  private static <T> T getAnnotation(Map<String, String> annotations, String key, TypeReference<T> typeReference) {
    return getAnnotation(annotations, key, typeReference, null);
  }

  private static <T> T getAnnotation(Map<String, String> annotations, String key, TypeReference<T> typeReference, T defaultValue) {
    String value = annotations.get(key);
    if (value == null) {
      return defaultValue;
    }

    try {
      return objectMapper.readValue(value, typeReference);
    } catch (Exception e) {
      log.warn("Illegally annotated resource for '" + key + "': " + e);
      return null;
    }
  }

  public static void annotateManifest(KubernetesManifest manifest, KubernetesManifestSpinnakerRelationships relationships) {
    Map<String, String> annotations = manifest.getAnnotations();
    storeAnnotations(annotations, relationships);

    manifest.getSpecTemplateAnnotations().flatMap(a -> {
      storeAnnotations(a, relationships);
      return Optional.empty();
    });
  }

  public static void annotateManifest(KubernetesManifest manifest, Moniker moniker) {
    Map<String, String> annotations = manifest.getAnnotations();
    storeAnnotations(annotations, moniker);

    manifest.getSpecTemplateAnnotations().flatMap(a -> {
      storeAnnotations(a, moniker);
      return Optional.empty();
    });
  }

  public static void annotateManifest(KubernetesManifest manifest, Artifact artifact) {
    Map<String, String> annotations = manifest.getAnnotations();
    storeAnnotations(annotations, artifact);

    manifest.getSpecTemplateAnnotations().flatMap(a -> {
      storeAnnotations(a, artifact);
      return Optional.empty();
    });
  }

  private static void storeAnnotations(Map<String, String> annotations, Moniker moniker) {
    if (moniker == null) {
      throw new IllegalArgumentException("Every resource deployed via spinnaker must be assigned a moniker");
    }

    storeAnnotation(annotations, CLUSTER, moniker.getCluster());
    storeAnnotation(annotations, APPLICATION, moniker.getApp());
    storeAnnotation(annotations, STACK, moniker.getStack());
    storeAnnotation(annotations, DETAIL, moniker.getDetail());
  }


  private static void storeAnnotations(Map<String, String> annotations, KubernetesManifestSpinnakerRelationships relationships) {
    if (relationships == null) {
      return;
    }

    storeAnnotation(annotations, LOAD_BALANCERS, relationships.getLoadBalancers());
    storeAnnotation(annotations, SECURITY_GROUPS, relationships.getSecurityGroups());
  }

  private static void storeAnnotations(Map<String, String> annotations, Artifact artifact) {
    if (artifact == null) {
      return;
    }

    storeAnnotation(annotations, TYPE, artifact.getType());
    storeAnnotation(annotations, NAME, artifact.getName());
    storeAnnotation(annotations, LOCATION, artifact.getLocation());
    storeAnnotation(annotations, VERSION, artifact.getVersion());
  }

  public static KubernetesManifestSpinnakerRelationships getManifestRelationships(KubernetesManifest manifest) {
    Map<String, String> annotations = manifest.getAnnotations();

    return new KubernetesManifestSpinnakerRelationships()
        .setLoadBalancers(getAnnotation(annotations, LOAD_BALANCERS, new TypeReference<List<String>>() {}))
        .setSecurityGroups(getAnnotation(annotations, SECURITY_GROUPS, new TypeReference<List<String>>() {}));
  }

  public static Artifact getArtifact(KubernetesManifest manifest) {
    Map<String, String> annotations = manifest.getAnnotations();

    return Artifact.builder()
        .type(getAnnotation(annotations, TYPE, new TypeReference<String>() {}))
        .name(getAnnotation(annotations, NAME, new TypeReference<String>() {}))
        .location(getAnnotation(annotations, LOCATION, new TypeReference<String>() {}))
        .version(getAnnotation(annotations, VERSION, new TypeReference<String>() {}))
        .build();
  }

  public static Moniker getMoniker(KubernetesManifest manifest) {
    Names parsed = Names.parseName(manifest.getName());
    Map<String, String> annotations = manifest.getAnnotations();

    return Moniker.builder()
        .cluster(getAnnotation(annotations, CLUSTER, new TypeReference<String>() {}, parsed.getCluster()))
        .app(getAnnotation(annotations, APPLICATION, new TypeReference<String>() {}, parsed.getApp()))
        .stack(getAnnotation(annotations, STACK, new TypeReference<String>() {}, null))
        .detail(getAnnotation(annotations, DETAIL, new TypeReference<String>() {}, null))
        .sequence(getAnnotation(annotations, DEPLOYMENT_REVISION, new TypeReference<Integer>() {}, null))
        .build();
  }

  public static KubernetesCachingProperties getCachingProperties(KubernetesManifest manifest) {
    Map<String, String> annotations = manifest.getAnnotations();

    return KubernetesCachingProperties.builder()
        .ignore(getAnnotation(annotations, IGNORE_CACHING, new TypeReference<Boolean>() {}, false))
        .build();
  }

  public static KubernetesManifest getLastAppliedConfiguration(KubernetesManifest manifest) {
    Map<String, String> annotations = manifest.getAnnotations();

    return getAnnotation(annotations, KUBECTL_LAST_APPLIED_CONFIGURATION, new TypeReference<KubernetesManifest>() { }, null);
  }
}
