package streams.kafka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.admin.AdminClient
import org.neo4j.logging.Log
import streams.utils.KafkaValidationUtils
import streams.utils.StreamsUtils
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class KafkaAdminService(private val props: KafkaConfiguration, private val allTopics: List<String>, private val log: Log) : AdminService {
    private val client = AdminClient.create(props.asProperties())
    private val kafkaTopics: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val isAutoCreateTopicsEnabled = KafkaValidationUtils.isAutoCreateTopicsEnabled(client)
    private lateinit var job: Job

    override fun start() {
        if (!isAutoCreateTopicsEnabled) {
            job = GlobalScope.launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        kafkaTopics += client.listTopics().names().get()
                    } catch (e: Exception) {
                        log.warn("""Cannot retrieve valid topics because the following exception, 
                            |next attempt is in ${props.topicDiscoveryPollingInterval} ms:
                        """.trimMargin(), e)
                    }
                    delay(props.topicDiscoveryPollingInterval)
                }
                client.close()
            }
        }
    }

    override fun stop() {
        StreamsUtils.ignoreExceptions({
            runBlocking {
                job.cancelAndJoin()
            }
        }, UninitializedPropertyAccessException::class.java)
    }

    override fun isValidTopic(topic: String) = when (isAutoCreateTopicsEnabled) {
        true -> true
        else -> kafkaTopics.contains(topic)
    }

    override fun getInvalidTopics() = KafkaValidationUtils.getInvalidTopics(client, allTopics)
}