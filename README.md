# Rocket-Trading
Team Rocket's trading platform.

# Code Quality
Checkstyle findings are reported by Jenkins without failing the build. To generate the report locally:

	cd starter
	mvn -B checkstyle:checkstyle

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