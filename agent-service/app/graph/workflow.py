from langgraph.graph import StateGraph, START, END
from langgraph.prebuilt import ToolNode
from app.model.agent_state import AgentState
import app.service.ai_service as ai_service
import app.graph.edges as edges
from app.graph.tools import tools

def build_graph():
    workflow = StateGraph(AgentState)

    # Add Nodes
    workflow.add_node(edges.NODE_REWRITE, ai_service.rewrite_node)
    workflow.add_node(edges.NODE_ANALYZE, ai_service.analyze_node)
    workflow.add_node(edges.NODE_CLARIFY, ai_service.clarify_node)
    workflow.add_node(edges.NODE_RETRIEVE, ai_service.retrieve_node)
    workflow.add_node(edges.NODE_GRADE, ai_service.grade_node)
    workflow.add_node(edges.NODE_WEB_SEARCH, ai_service.web_search_node)
    workflow.add_node(edges.NODE_MARK_LOW_CONFIDENCE, ai_service.mark_low_confidence_node)
    workflow.add_node(edges.NODE_GENERATE, ai_service.generate_node)
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
