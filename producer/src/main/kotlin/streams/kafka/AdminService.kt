package streams.kafka

import org.neo4j.logging.Log

interface AdminService {

    fun start()

    fun stop()

    fun isValidTopic(topic: String): Boolean

    fun getInvalidTopics(): List<String>

    companion object{
        fun getInstance(props: KafkaConfiguration, allTopics: List<String>, log: Log): AdminService = when (props.adminClientApiEnabled) {
            true -> KafkaAdminService(props, allTopics, log)
            else -> DefaultAdminService(log)
        }
    }
}