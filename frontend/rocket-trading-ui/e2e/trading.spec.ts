import { test, expect } from '@playwright/test';

test('register, sign in, quote, buy, automatic settlement, sell, reject and sign out', async ({ page }, testInfo) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  const email = `browser-${Date.now()}@example.com`;
  const registration = page.getByRole('form', { name: 'Register', exact: true });
  await registration.getByLabel('Name', { exact: true }).fill('Browser Trader');
  await registration.getByLabel('Email', { exact: true }).fill(email);
  await registration.getByRole('button', { name: 'Register', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Client registered');
  const signIn = page.getByRole('form', { name: 'Sign in', exact: true });
  await signIn.getByLabel('Email', { exact: true }).fill(email);
  await signIn.getByRole('button', { name: 'Sign in', exact: true }).click();
  const portfolio = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Portfolio', exact: true }) });
  await expect(portfolio).toContainText('10,000.00');
  await page.getByRole('button', { name: 'Fetch quote' }).click();
  await expect(page.getByRole('status')).toContainText('Current quote for AAPL');

  const form = page.getByRole('form', { name: 'Place order' });
  await form.getByLabel('Quantity', { exact: true }).fill('5');
  await form.getByRole('button', { name: 'Submit order' }).click();
  const history = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Recent orders' }) });
  await expect(history.locator('tbody tr').first()).toContainText('FILLED');
  await expect(history.locator('tbody tr').first()).toContainText('5 @ 101.00');
  await expect(portfolio).toContainText('9,495.00');
  await expect(portfolio.locator('tbody tr')).toContainText('5.00');

  // A page reload restores the session and persisted portfolio without a manual refresh.
  await page.reload();
  await expect(portfolio).toContainText('9,495.00');
  await form.getByRole('combobox', { name: 'Side', exact: true }).selectOption('SELL');
  await form.getByLabel('Quantity', { exact: true }).fill('2');
  await form.getByRole('button', { name: 'Submit order' }).click();
  await expect(history.locator('tbody tr').first()).toContainText('SELL');
  await expect(history.locator('tbody tr').first()).toContainText('FILLED');
  await expect(portfolio).toContainText('9,695.00');
  await expect(portfolio.locator('tbody tr')).toContainText('3.00');

  await form.getByLabel('Quantity', { exact: true }).fill('999');
  await form.getByRole('button', { name: 'Submit order' }).click();
  await expect(history.locator('tbody tr').first()).toContainText('REJECTED');
  await expect(history.locator('tbody tr').first()).toContainText('Available holdings');
  await expect(portfolio).toContainText('9,695.00');
  await page.screenshot({ path: testInfo.outputPath('trading-dashboard.png'), fullPage: true });
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page.getByText('No active session.')).toBeVisible();
  await expect(portfolio).toContainText('Sign in to load the portfolio.');
  expect(errors).toEqual([]);
});
