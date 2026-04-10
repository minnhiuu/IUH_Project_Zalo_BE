from langgraph.graph import StateGraph, START, END
from langgraph.prebuilt import ToolNode
from app.graph.state import AgentState
import app.graph.nodes as nodes
import app.graph.edges as edges
from app.graph.tools import tools
from langgraph.checkpoint.mongodb.aio import AsyncMongoDBSaver
from motor.motor_asyncio import AsyncIOMotorClient
from app.core.config import settings

def build_graph():
    workflow = StateGraph(AgentState)

    # Add Nodes
    workflow.add_node(edges.NODE_REWRITE, nodes.rewrite_node)
    workflow.add_node(edges.NODE_ANALYZE, nodes.analyze_node)
    workflow.add_node(edges.NODE_CLARIFY, nodes.clarify_node)
    workflow.add_node(edges.NODE_RETRIEVE, nodes.retrieve_node)
    workflow.add_node(edges.NODE_GRADE, nodes.grade_node)
    workflow.add_node(edges.NODE_WEB_SEARCH, nodes.web_search_node)
    workflow.add_node(edges.NODE_MARK_LOW_CONFIDENCE, nodes.mark_low_confidence_node)
    workflow.add_node(edges.NODE_GENERATE, nodes.generate_node)
    workflow.add_node(edges.NODE_ACTION, ToolNode(tools))

    # Define edges
    workflow.add_edge(START, edges.NODE_REWRITE)
    workflow.add_edge(edges.NODE_REWRITE, edges.NODE_ANALYZE)
    
    workflow.add_conditional_edges(
        edges.NODE_ANALYZE, 
        edges.next_after_analyze,
        {
            edges.NODE_CLARIFY: edges.NODE_CLARIFY,
            edges.NODE_RETRIEVE: edges.NODE_RETRIEVE,
            edges.NODE_GENERATE: edges.NODE_GENERATE
        }
    )
    
    workflow.add_edge(edges.NODE_CLARIFY, END)
    
    workflow.add_edge(edges.NODE_RETRIEVE, edges.NODE_GRADE)
    
    workflow.add_conditional_edges(
        edges.NODE_GRADE,
        edges.next_after_grade,
        {
            edges.NODE_GENERATE: edges.NODE_GENERATE,
            edges.NODE_WEB_SEARCH: edges.NODE_WEB_SEARCH,
            edges.NODE_MARK_LOW_CONFIDENCE: edges.NODE_MARK_LOW_CONFIDENCE
        }
    )
    
    workflow.add_edge(edges.NODE_WEB_SEARCH, edges.NODE_GENERATE)
    workflow.add_edge(edges.NODE_MARK_LOW_CONFIDENCE, edges.NODE_GENERATE)
    
    # Tool calling loop
    workflow.add_conditional_edges(
        edges.NODE_GENERATE,
        edges.should_continue,
        {
            edges.NODE_ACTION: edges.NODE_ACTION,
            END: END
        }
    )
    
    workflow.add_edge(edges.NODE_ACTION, edges.NODE_GENERATE)

    return workflow

# Global checkpointer singleton
_checkpointer = None

async def get_checkpointer():
    global _checkpointer
    if _checkpointer is None:
        client = AsyncIOMotorClient(settings.mongodb_uri)
        db_name = client.get_default_database().name
        _checkpointer = AsyncMongoDBSaver(client, db_name=db_name)
        # Ensure collections exist by doing a no-op or similar if needed
        # AsyncMongoDBSaver internal setup is usually lazy but reliable
    return _checkpointer

async def get_compiled_graph():
    checkpointer = await get_checkpointer()
    graph = build_graph()
    return graph.compile(checkpointer=checkpointer)
