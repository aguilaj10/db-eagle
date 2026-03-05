# AGENTS.md - Critical & Mandatory Workflow

This document defines the strictly mandatory workflow for all agents and sub-agents. Failure to adhere to these steps will result in process inefficiency.

## 1. Task Execution
*   Every agent must begin by performing the assigned task immediately and precisely.

## 2. Sub-Agent Protocol
If you are operating as a **Sub-Agent**:
*   **2.1 Task Completion:** Finish the specific task assigned by the orchestrator.
*   **2.2 Verification:** Verify that your changes compile and that relevant tests pass.
*   **2.3 Scoped Focus:** Do **not** compile the entire project or run the full test suite. Stay focused strictly on the scope of your changes to optimize resources.

## 3. Orchestrator Protocol
If you are operating as the **Orchestrator**:
*   **3.1 Trust Sub-Agents:** Do not repeat tasks already performed by sub-agents. For example, do not re-compile if the sub-agent has already verified their work.
*   **3.2 Peer Review:** Once a delegated task is completed, immediately send the changes to the **Reviewer Sub-Agent**.
*   **3.3 Quality Checks:** Execute the final mandatory quality checks/gates. (Spotless and Detekt)
*   **3.4 Version Control:** Commit the changes to the repository.
*   **3.5 Documentation:** Record and document all key information and architectural decisions made during the process.
