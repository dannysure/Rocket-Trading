# Rocket-Trading
Team Rocket's trading platform.

# Java backend starter

The initial Java backend skeleton now lives in `backend/trading-core/` as a Maven module.

It includes:

- initial trading domain objects such as orders, quotes, portfolios, and audit events,
- service skeletons with TODO methods for order flow, portfolio updates, quote lookup, and audit recording,
- JUnit 5 tests that define the first TDD slices,
- disabled backlog tests for the next business-rule scenarios.

Run the trading-core tests from the repository root:

	cd backend\trading-core
	mvn test

# Code Quality
Checkstyle findings are reported by Jenkins without failing the build. To generate the report locally:

	cd backend\trading-core
	mvn -B test

# Branching Strategies
We are going to have 3 branches: prod, dev, and features.  
Prod- Main production with weekly pushes.  
Dev- Working branch with daily pushes or as needed, merged into prod weekly.  
Features- Individual branches for specific changes merged into dev once complete.  
Branch Naming Convention: Feature/name/feature name  
Commit Message Convention: Be specific and limit to 1-2 lines.   

# Team Members
Aneesh Sallaram- Apple Pie  
Manan Shah- Marshmallows  
Walker Manuel- Watermelon  
Daniel Xu- Deviled Eggs  
Sam Adams- Spam 