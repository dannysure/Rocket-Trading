"""
Import validation - ensures all API modules can be imported successfully
Run this to verify the FastAPI structure is correctly set up
Must be run from root directory: python -m tests.validate_api
"""
import sys
import os

# Add root directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

def test_imports():
    """Test that all API modules import correctly"""
    errors = []
    
    tests = [
        ("api.database", "Database module with connection pooling"),
        ("api.models", "Pydantic response models"),
        ("api.queries.portfolio", "Portfolio query functions"),
        ("api.queries.trading", "Trading query functions"),
        ("api.queries.analytics", "Analytics query functions"),
        ("api.queries.watchlist", "Watchlist query functions"),
        ("api.routes.portfolio", "Portfolio API routes"),
        ("api.routes.trading", "Trading API routes"),
        ("api.routes.analytics", "Analytics API routes"),
        ("api.routes.watchlist", "Watchlist API routes"),
        ("api.main", "Main FastAPI application"),
    ]
    
    print("🔍 Validating API Structure...\n")
    
    for module_name, description in tests:
        try:
            __import__(module_name)
            print(f"✅ {module_name:30} - {description}")
        except Exception as e:
            print(f"❌ {module_name:30} - {description}")
            print(f"   Error: {str(e)}")
            errors.append((module_name, str(e)))
    
    print("\n" + "="*80)
    if errors:
        print(f"❌ {len(errors)} import(s) failed")
        return False
    else:
        print("✅ All imports successful - API structure is valid!")
        return True

if __name__ == "__main__":
    success = test_imports()
    sys.exit(0 if success else 1)
