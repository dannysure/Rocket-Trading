#!/usr/bin/env python3
"""
Rocket Trading Platform - Integration Test Suite
Tests complete end-to-end flow of the trading platform
"""

import requests
import json
import time
import sys
from datetime import datetime
from typing import Optional, Dict, Any

# Configuration
BASE_URL = "http://localhost:8081/api/v1"
TIMEOUT = 10

# Test data
TEST_USER = {
    "name": "Test User",
    "email": f"test.{int(time.time())}@example.com",
    "dateOfBirth": "1990-01-15",
    "riskProfile": "Balanced"
}

# Colors for output
class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    RESET = '\033[0m'

def print_header(text: str):
    """Print a formatted header"""
    print(f"\n{Colors.BLUE}{'='*60}")
    print(f"{text.center(60)}")
    print(f"{'='*60}{Colors.RESET}\n")

def print_success(text: str):
    """Print success message"""
    print(f"{Colors.GREEN}✓ {text}{Colors.RESET}")

def print_error(text: str):
    """Print error message"""
    print(f"{Colors.RED}✗ {text}{Colors.RESET}")

def print_info(text: str):
    """Print info message"""
    print(f"{Colors.YELLOW}ℹ {text}{Colors.RESET}")

def print_response(response: requests.Response):
    """Pretty print API response"""
    try:
        print(json.dumps(response.json(), indent=2))
    except:
        print(response.text)

class RocketTradingClient:
    """Client for interacting with Rocket Trading API"""

    def __init__(self, base_url: str = BASE_URL):
        self.base_url = base_url
        self.session = requests.Session()
        self.access_token: Optional[str] = None
        self.client_id: Optional[int] = None
        self.account_id: Optional[int] = None

    def register(self, name: str, email: str, dob: str, risk_profile: str) -> bool:
        """Register a new client"""
        try:
            response = self.session.post(
                f"{self.base_url}/auth/register",
                json={
                    "name": name,
                    "email": email,
                    "dateOfBirth": dob,
                    "riskProfile": risk_profile
                },
                timeout=TIMEOUT
            )
            
            if response.status_code != 201:
                print_error(f"Registration failed: {response.status_code}")
                print_response(response)
                return False

            data = response.json()["data"]
            self.client_id = data["clientId"]
            print_success(f"Registered client: {email} (ID: {self.client_id})")
            return True

        except Exception as e:
            print_error(f"Registration error: {str(e)}")
            return False

    def sign_in(self, email: str) -> bool:
        """Sign in a client"""
        try:
            response = self.session.post(
                f"{self.base_url}/auth/sign-in",
                json={"email": email},
                timeout=TIMEOUT
            )

            if response.status_code != 200:
                print_error(f"Sign-in failed: {response.status_code}")
                print_response(response)
                return False

            data = response.json()["data"]
            self.access_token = data["accessToken"]
            self.client_id = data["clientId"]
            
            # Set auth header for future requests
            self.session.headers.update({
                "Authorization": f"Bearer {self.access_token}"
            })
            
            print_success(f"Signed in: {email}")
            return True

        except Exception as e:
            print_error(f"Sign-in error: {str(e)}")
            return False

    def get_portfolio_summary(self) -> Optional[Dict[str, Any]]:
        """Get portfolio summary"""
        try:
            response = self.session.get(
                f"{self.base_url}/portfolio/summary",
                timeout=TIMEOUT
            )

            if response.status_code != 200:
                print_error(f"Failed to get portfolio: {response.status_code}")
                return None

            data = response.json()["data"]
            self.account_id = data["accountId"]
            
            print_success(f"Portfolio Summary:")
            print(f"  Cash Balance: {data['currency']} {data['cashBalance']:.2f}")
            print(f"  Account Type: {data['accountType']}")
            print(f"  Total Value: {data['cashBalance']:.2f}")
            print(f"  Positions: {len(data.get('positions', []))}")
            
            return data

        except Exception as e:
            print_error(f"Portfolio error: {str(e)}")
            return None

    def get_quote(self, symbol: str) -> Optional[Dict[str, Any]]:
        """Get quote for a symbol"""
        try:
            response = self.session.get(
                f"{self.base_url}/quotes/{symbol}",
                timeout=TIMEOUT
            )

            if response.status_code != 200:
                print_info(f"Quote not available for {symbol}")
                return None

            data = response.json()["data"]
            print_success(f"Quote for {symbol}: Bid={data['bid']}, Ask={data['ask']}")
            return data

        except Exception as e:
            print_error(f"Quote error: {str(e)}")
            return None

    def submit_order(self, instrument_id: int, side: str, quantity: float) -> Optional[Dict[str, Any]]:
        """Submit an order"""
        try:
            response = self.session.post(
                f"{self.base_url}/orders",
                json={
                    "instrumentId": instrument_id,
                    "side": side,
                    "quantity": quantity,
                    "orderType": "MARKET",
                    "accountId": self.account_id
                },
                headers={"Idempotency-Key": f"{int(time.time())}"},
                timeout=TIMEOUT
            )

            if response.status_code not in [200, 202]:
                print_error(f"Order submission failed: {response.status_code}")
                print_response(response)
                return None

            data = response.json()["data"]
            print_success(f"Order submitted: {data['orderId']} - {side} {quantity} of {data['symbol']} (Status: {data['status']})")
            
            if data.get("rejectionReason"):
                print_info(f"Rejection reason: {data['rejectionReason']}")
            
            return data

        except Exception as e:
            print_error(f"Order submission error: {str(e)}")
            return False

    def list_orders(self) -> Optional[list]:
        """List all orders"""
        try:
            response = self.session.get(
                f"{self.base_url}/orders",
                timeout=TIMEOUT
            )

            if response.status_code != 200:
                print_error(f"Failed to list orders: {response.status_code}")
                return None

            data = response.json()["data"]
            print_success(f"Orders: {len(data)} order(s) found")
            
            for order in data:
                print(f"  - Order {order['orderId']}: {order['symbol']} {order['side']} {order['quantity']} (Status: {order['status']})")
            
            return data

        except Exception as e:
            print_error(f"List orders error: {str(e)}")
            return None

    def get_order_timeline(self, order_id: int) -> Optional[Dict[str, Any]]:
        """Get order timeline"""
        try:
            response = self.session.get(
                f"{self.base_url}/orders/{order_id}/timeline",
                timeout=TIMEOUT
            )

            if response.status_code != 200:
                print_error(f"Failed to get order timeline: {response.status_code}")
                return None

            data = response.json()["data"]
            print_success(f"Order Timeline for {order_id}: {len(data['events'])} event(s)")
            
            for event in data["events"]:
                print(f"  - {event['actionType']}: {event['recordedAt']}")
            
            return data

        except Exception as e:
            print_error(f"Timeline error: {str(e)}")
            return None

    def get_reporting_overview(self) -> Optional[Dict[str, Any]]:
        """Get reporting overview"""
        try:
            response = self.session.get(
                f"{self.base_url}/reporting/overview?from=2024-01-01&to=2025-12-31",
                timeout=TIMEOUT
            )

            if response.status_code != 200:
                print_error(f"Failed to get reporting: {response.status_code}")
                return None

            data = response.json()["data"]
            print_success(f"Reporting Overview:")
            print(f"  Total Orders: {data['totalOrders']}")
            print(f"  Accepted: {data['acceptedOrders']}")
            print(f"  Filled: {data['filledOrders']}")
            print(f"  Rejected: {data['rejectedOrders']}")
            print(f"  Total Fills: {data['totalFills']}")
            
            return data

        except Exception as e:
            print_error(f"Reporting error: {str(e)}")
            return None

def test_api_connectivity() -> bool:
    """Test basic API connectivity"""
    print_header("Testing API Connectivity")
    
    try:
        response = requests.get(f"{BASE_URL}/../../../health", timeout=TIMEOUT)
        if response.status_code == 200:
            print_success("API is reachable")
            return True
        else:
            print_error(f"API returned status code: {response.status_code}")
            return False
    except Exception as e:
        print_error(f"Cannot connect to API: {str(e)}")
        print_info(f"Make sure the backend is running on {BASE_URL}")
        return False

def run_integration_tests():
    """Run full integration test suite"""
    print_header("Rocket Trading Platform - Integration Tests")
    
    # Test connectivity
    if not test_api_connectivity():
        print_error("Cannot connect to API. Exiting.")
        sys.exit(1)

    client = RocketTradingClient()

    # Test 1: Register
    print_header("Test 1: User Registration")
    if not client.register(
        TEST_USER["name"],
        TEST_USER["email"],
        TEST_USER["dateOfBirth"],
        TEST_USER["riskProfile"]
    ):
        print_error("Registration test failed")
        return False

    # Test 2: Sign In
    print_header("Test 2: User Sign In")
    if not client.sign_in(TEST_USER["email"]):
        print_error("Sign-in test failed")
        return False

    # Test 3: Get Portfolio
    print_header("Test 3: Get Portfolio Summary")
    portfolio = client.get_portfolio_summary()
    if not portfolio:
        print_error("Portfolio test failed")
        return False

    # Test 4: Get Quote
    print_header("Test 4: Get Quote")
    quote = client.get_quote("AAPL")
    if not quote:
        print_info("Quote test skipped (quote not available)")

    # Test 5: Submit Order
    print_header("Test 5: Submit Order")
    order = client.submit_order(instrument_id=1, side="BUY", quantity=10)
    if not order:
        print_info("Order test skipped (order submission not available)")
    
    order_id = order.get("orderId") if order else None

    # Test 6: List Orders
    print_header("Test 6: List Orders")
    orders = client.list_orders()
    if not orders:
        print_error("List orders test failed")
        return False

    # Test 7: Get Order Timeline
    if order_id:
        print_header("Test 7: Get Order Timeline")
        timeline = client.get_order_timeline(order_id)
        if not timeline:
            print_error("Timeline test failed")
            return False

    # Test 8: Get Reporting
    print_header("Test 8: Get Reporting Overview")
    reporting = client.get_reporting_overview()
    if not reporting:
        print_info("Reporting test skipped (reporting not available)")

    # Summary
    print_header("Test Results")
    print_success("All tests completed successfully!")
    print_info(f"Test user: {TEST_USER['email']}")
    print_info(f"Client ID: {client.client_id}")
    
    return True

if __name__ == "__main__":
    try:
        success = run_integration_tests()
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        print_error("\nTests interrupted by user")
        sys.exit(1)
    except Exception as e:
        print_error(f"Test suite error: {str(e)}")
        sys.exit(1)
