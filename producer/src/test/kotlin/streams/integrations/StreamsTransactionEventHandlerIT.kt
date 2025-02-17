package streams.integrations

import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.neo4j.test.rule.DbmsRule
import org.neo4j.test.rule.ImpermanentDbmsRule
import streams.events.NodeChange
import streams.events.OperationType
import streams.events.RelationshipPayload
import streams.extensions.execute
import streams.mocks.MockStreamsEventRouter
import streams.setConfig
import streams.start
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Suppress("DEPRECATION")
class StreamsTransactionEventHandlerIT {

    val db: DbmsRule = ImpermanentDbmsRule()
            .setConfig("streams.router", "streams.mocks.MockStreamsEventRouter")

    @Before
    fun setUp() {
        MockStreamsEventRouter.reset()
        db.start()
    }

    @After
    fun tearDown() {
        db.shutdown()
    }

    @Test fun testNodes() {
        db.execute("CREATE (:Person {name:'Omar', age: 30}), (:Person {name:'Andrea', age: 31})")

        assertEquals(2,MockStreamsEventRouter.events.size)
        assertEquals(OperationType.created,MockStreamsEventRouter.events[0].meta.operation)
        assertEquals(OperationType.created,MockStreamsEventRouter.events[1].meta.operation)
        assertEquals(2,MockStreamsEventRouter.events[0].meta.txEventsCount)
        assertEquals(2,MockStreamsEventRouter.events[1].meta.txEventsCount)
        assertEquals(0,MockStreamsEventRouter.events[0].meta.txEventId)
        assertEquals(1,MockStreamsEventRouter.events[1].meta.txEventId)
        assertNotNull(MockStreamsEventRouter.events[0].meta.source["hostname"])
        assertNotNull(MockStreamsEventRouter.events[1].meta.source["hostname"])

        MockStreamsEventRouter.reset()

        db.execute("MATCH (o:Person {name:'Omar'}), (a:Person {name:'Andrea'}) " +
                "SET o:Test " +
                "REMOVE o:Person " +
                "SET o.age = 31 " +
                "SET o.surname = 'unknown' " +
                "REMOVE o.name " +
                "SET a:Marked ")

        assertEquals(2,MockStreamsEventRouter.events.size)
        assertEquals(OperationType.updated,MockStreamsEventRouter.events[0].meta.operation)
        assertEquals(OperationType.updated,MockStreamsEventRouter.events[1].meta.operation)
        assertEquals(2,MockStreamsEventRouter.events[0].meta.txEventsCount)
        assertEquals(2,MockStreamsEventRouter.events[1].meta.txEventsCount)
        assertEquals(0,MockStreamsEventRouter.events[0].meta.txEventId)
        assertEquals(1,MockStreamsEventRouter.events[1].meta.txEventId)

        val beforeOmarSet : NodeChange = MockStreamsEventRouter.events[0].payload.before as NodeChange
        assertEquals(mapOf("name" to "Omar", "age" to 30L) , beforeOmarSet.properties)

        val beforeAndreaSet : NodeChange = MockStreamsEventRouter.events[1].payload.before as NodeChange
        assertEquals(mapOf("name" to "Andrea", "age" to 31L) , beforeAndreaSet.properties)

        MockStreamsEventRouter.reset()
        db.execute("MATCH (o:Marked) DELETE o ")

        assertEquals(1,MockStreamsEventRouter.events.size)
        assertEquals(OperationType.deleted,MockStreamsEventRouter.events[0].meta.operation)
        val before : NodeChange = MockStreamsEventRouter.events[0].payload.before as NodeChange
        assertEquals(listOf("Person","Marked") , before.labels)
        assertEquals(mapOf("name" to "Andrea", "age" to 31L) , before.properties)

        assertEquals(1,MockStreamsEventRouter.events[0].meta.txEventsCount)
        assertEquals(0,MockStreamsEventRouter.events[0].meta.txEventId)

    }

    @Test fun testRelationships() {
        db.execute("CREATE (:Person {name:'Omar', age: 30}), (:Person {name:'Andrea', age: 31})")
        MockStreamsEventRouter.reset()

        db.execute("MATCH (o:Person {name:'Omar', age: 30}), (a:Person {name:'Andrea', age: 31}) " +
                "CREATE (o)-[r:KNOWS]->(a)")

        assertEquals(1,MockStreamsEventRouter.events.size)
        assertEquals(OperationType.created,MockStreamsEventRouter.events[0].meta.operation)
        assertEquals(1,MockStreamsEventRouter.events[0].meta.txEventsCount)
        assertEquals(0,MockStreamsEventRouter.events[0].meta.txEventId)

        MockStreamsEventRouter.reset()
        db.execute("MATCH (o:Person {name:'Omar', age: 30})-[r:KNOWS]->(a:Person {name:'Andrea', age: 31}) " +
                "SET r.touched = true")

        assertEquals(1,MockStreamsEventRouter.events.size)
        assertEquals(OperationType.updated,MockStreamsEventRouter.events[0].meta.operation)
        assertEquals(1,MockStreamsEventRouter.events[0].meta.txEventsCount)
        assertEquals(0,MockStreamsEventRouter.events[0].meta.txEventId)

        MockStreamsEventRouter.reset()
        db.execute("MATCH (o:Person {name:'Omar', age: 30})-[r:KNOWS]->(a:Person {name:'Andrea', age: 31}) " +
                "DELETE r")

        assertEquals(1,MockStreamsEventRouter.events.size)
        assertEquals(OperationType.deleted,MockStreamsEventRouter.events[0].meta.operation)
        assertEquals(1,MockStreamsEventRouter.events[0].meta.txEventsCount)
        assertEquals(0,MockStreamsEventRouter.events[0].meta.txEventId)
    }

    @Test fun testDetachDelete() {
        db.execute("CREATE (o:Person:Start {name:'Omar', age: 30})-[r:KNOWS {since: datetime()}]->(a:Person:End {name:'Andrea', age: 31})")
        MockStreamsEventRouter.reset()
        db.execute("MATCH (n) DETACH DELETE n")

        assertEquals(3,MockStreamsEventRouter.events.size)
        assertEquals(OperationType.deleted,MockStreamsEventRouter.events[0].meta.operation)
        assertEquals(3,MockStreamsEventRouter.events[0].meta.txEventsCount)
        assertEquals(0,MockStreamsEventRouter.events[0].meta.txEventId)

        assertEquals(OperationType.deleted,MockStreamsEventRouter.events[1].meta.operation)
        assertEquals(3,MockStreamsEventRouter.events[1].meta.txEventsCount)
        assertEquals(1,MockStreamsEventRouter.events[1].meta.txEventId)

        assertEquals(OperationType.deleted,MockStreamsEventRouter.events[2].meta.operation)
        assertEquals(3,MockStreamsEventRouter.events[2].meta.txEventsCount)
        assertEquals(2,MockStreamsEventRouter.events[2].meta.txEventId)

        val relPayload : RelationshipPayload  = MockStreamsEventRouter.events[2].payload as RelationshipPayload
        assertEquals(listOf("Person","Start"),relPayload.start.labels)
        assertEquals(listOf("Person","End"),relPayload.end.labels)
    }
}
