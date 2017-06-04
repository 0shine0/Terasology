/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.telemetry;

import com.snowplowanalytics.snowplow.tracker.DevicePlatform;
import com.snowplowanalytics.snowplow.tracker.Tracker;
import com.snowplowanalytics.snowplow.tracker.emitter.BatchEmitter;
import com.snowplowanalytics.snowplow.tracker.emitter.Emitter;
import com.snowplowanalytics.snowplow.tracker.emitter.RequestCallback;
import com.snowplowanalytics.snowplow.tracker.events.Unstructured;
import com.snowplowanalytics.snowplow.tracker.http.ApacheHttpClientAdapter;
import com.snowplowanalytics.snowplow.tracker.http.HttpClientAdapter;
import com.snowplowanalytics.snowplow.tracker.payload.SelfDescribingJson;
import com.snowplowanalytics.snowplow.tracker.payload.TrackerPayload;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

@RegisterSystem // maybe authority functionality in parameters
public class TelemetrySystem extends BaseComponentSystem {

    private Emitter emitter;

    private Tracker tracker;

    private final String appId = "terasology";

    private final URL url = urlInit("http","localhost",80);

    private final DevicePlatform platform = DevicePlatform.Desktop;

    private static final Logger logger = LoggerFactory.getLogger(TelemetrySystem.class);

    private URL urlInit(String protocal, String host, int port) {
        URL url = null;
        try {
            url = new URL(protocal, host, port, "");
        } catch (MalformedURLException e) {
            logger.error("telemetry url mal formed");
            e.printStackTrace();
        }
        return url;
    }

    @Override
    public void initialise() {

        // Make a new client with custom concurrency rules
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setDefaultMaxPerRoute(50);

        // Make the client
        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(manager)
                .build();

        // Build the adapter
        HttpClientAdapter adapter = ApacheHttpClientAdapter.builder()
                .url(url.toString())
                .httpClient(client)
                .build();

        // Make a RequestCallback
        RequestCallback callback = new RequestCallback() {

            public void onSuccess(int successCount) {
                logger.info("Success sent, successCount: " + successCount);
            }

            public void onFailure(int successCount, List<TrackerPayload> failedEvents) {
                logger.warn("Failure, successCount: " + successCount + "\nfailedEvent:\n" + failedEvents.toString());
            }
        };

        // initialise emitter
        emitter = BatchEmitter.builder()
                .httpClientAdapter(adapter) // Required
                .threadCount(20) // Default is 50
                .requestCallback(callback)
                .bufferSize(1)
                .build();

        // Name space to identify the tracker
        String namespace = "org.terasology.telemetry.Telemetry";

        // initialise tracker
        tracker = new Tracker.TrackerBuilder(emitter, namespace, appId)
                .platform(platform)
                .build();

        systemContextTrack();
    }

    private void systemContextTrack() {

        String SCHEMA_OS = "iglu:org.terasology/systemContext/jsonschema/1-0-0";

        Map<String, Object> systemContext = new HashMap<String,Object>();

        String osName = System.getProperty("os.name");
        systemContext.put("osName", osName);

        String osVersion = System.getProperty("os.version");
        systemContext.put("osVersion", osVersion);

        String osArchitecture = System.getProperty("os.arch");
        systemContext.put("osArchitecture", osArchitecture);

        String javaVendor = System.getProperty("java.vendor");
        systemContext.put("javaVendor",javaVendor);

        String javaVersion = System.getProperty("java.version");
        systemContext.put("javaVersion",javaVersion);

        String jvmName = System.getProperty("java.vm.name");
        systemContext.put("jvmName",jvmName);

        String jvmVersion = System.getProperty("java.vm.version");
        systemContext.put("jvmVersion",jvmName);

        String openGLVendor = GL11.glGetString(GL11.GL_VENDOR);
        systemContext.put("openGLVendor",openGLVendor);

        String openGLVersion = GL11.glGetString(GL11.GL_VERSION);
        systemContext.put("openGLVersion",openGLVersion);

        String openGLRenderer = GL11.glGetString(GL11.GL_RENDERER);
        systemContext.put("openGLRenderer",openGLRenderer);

        int processorNumbers = Runtime.getRuntime().availableProcessors();
        systemContext.put("processorNumbers",processorNumbers);

        long memoryMaxByte = Runtime.getRuntime().maxMemory();
        int memoryMaxMb = (int) (memoryMaxByte/(1024*1024));
        systemContext.put("memoryMax",memoryMaxMb);

        SelfDescribingJson systemConextData = new SelfDescribingJson(SCHEMA_OS, systemContext);
        Unstructured osEvent = Unstructured.builder()
                .eventData(systemConextData)
                .build();

        tracker.track(osEvent);
    }
}
