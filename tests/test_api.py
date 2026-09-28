"""
Development script to test API endpoints locally
Usage: python test_api.py
"""
import requests
import os
from dotenv import load_dotenv

# Load environment
load_dotenv()

BASE_URL = "http://localhost:8000"

def test_health():
    """Test health endpoint"""
    print("\n✅ Testing /health endpoint...")
    resp = requests.get(f"{BASE_URL}/health")
    print(f"Status: {resp.status_code}")
    print(f"Response: {resp.json()}")

def test_portfolio(client_id=1):
    """Test portfolio endpoint"""
    print(f"\n✅ Testing /api/portfolio/{client_id} endpoint...")
    resp = requests.get(f"{BASE_URL}/api/portfolio/{client_id}")
    print(f"Status: {resp.status_code}")
    if resp.status_code == 200:
        data = resp.json()
        print(f"Client: {data['client_id']}")
        print(f"Total Portfolio Value: ${data['total_portfolio_value']:.2f}")
        print(f"Holdings Count: {len(data['holdings'])}")
    else:
        print(f"Error: {resp.json()}")

def test_trading_activity(client_id=1):
    """Test trading activity endpoint"""
    print(f"\n✅ Testing /api/trading/activity endpoint...")
    resp = requests.get(f"{BASE_URL}/api/trading/activity", params={"client_id": client_id})
    print(f"Status: {resp.status_code}")
    if resp.status_code == 200:
        data = resp.json()
        print(f"Total Orders: {data['total_orders']}")
        print(f"Filled Orders: {data['filled_orders']}")
        print(f"Fill Rate: {data['fill_rate_pct']:.1f}%")
    else:
        print(f"Error: {resp.json()}")

def test_platform_overview():
    """Test platform overview endpoint"""
    print(f"\n✅ Testing /api/analytics/platform-overview endpoint...")
    resp = requests.get(f"{BASE_URL}/api/analytics/platform-overview")
    print(f"Status: {resp.status_code}")
    if resp.status_code == 200:
        data = resp.json()
        print(f"Total Clients: {data['total_clients']}")
        print(f"Total Accounts: {data['total_accounts']}")
        print(f"Total Orders: {data['total_orders']}")
        print(f"Total Fills: {data['total_fills']}")
        print(f"Avg Fill Rate: {data['avg_fill_rate_pct']:.1f}%")
    else:
        print(f"Error: {resp.json()}")

if __name__ == "__main__":
    print("🧪 Testing LEAP Analytics API...")
    print(f"Base URL: {BASE_URL}")
    
    try:
        test_health()
        test_portfolio()
        test_trading_activity()
        test_platform_overview()
        print("\n✅ All tests passed!")
    except requests.exceptions.ConnectionError:
        print(f"\n❌ Could not connect to {BASE_URL}")
        print("   Make sure API is running: python api_server.py")
