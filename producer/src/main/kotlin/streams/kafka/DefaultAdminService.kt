package streams.kafka

import org.neo4j.logging.Log

class DefaultAdminService(private val log: Log) : AdminService {

    override fun start() {
        log.info("No need to start the AdminService to check the topic list. We'll consider the topic's auto creation enabled")
    }

    override fun stop() {} // Do nothing

    override fun isValidTopic(topic: String): Boolean = true

    override fun getInvalidTopics(): List<String> = emptyList()

}