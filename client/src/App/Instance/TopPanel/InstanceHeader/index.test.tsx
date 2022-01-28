/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {getProcessName} from 'modules/utils/instance';
import {InstanceHeader} from './index';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {variablesStore} from 'modules/stores/variables';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {operationsStore} from 'modules/stores/operations';
import {
  mockInstanceWithActiveOperation,
  mockInstanceWithoutOperations,
  mockInstanceWithParentInstance,
  mockOperationCreated,
  mockCanceledInstance,
} from './index.setup';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {Router, Route} from 'react-router';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {mockCallActivityProcessXML, mockProcessXML} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';
import {createMemoryHistory} from 'history';
import {panelStatesStore} from 'modules/stores/panelStates';
import {useNotifications} from 'modules/notifications';

jest.mock('modules/notifications', () => {
  const mockUseNotifications = {
    displayNotification: jest.fn(),
  };

  return {
    useNotifications: () => {
      return mockUseNotifications;
    },
  };
});

const createWrapper = (
  history = createMemoryHistory({initialEntries: ['/instances/1']})
) => {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <Router history={history}>
          <Route path="/instances/:processInstanceId">{children}</Route>
          <Route exact path="/instances">
            {children}
          </Route>
        </Router>
      </ThemeProvider>
    );
  };

  return Wrapper;
};

describe('InstanceHeader', () => {
  afterEach(() => {
    operationsStore.reset();
    variablesStore.reset();
    currentInstanceStore.reset();
    singleInstanceDiagramStore.reset();
    authenticationStore.reset();
  });

  it('should show skeleton before instance data is available', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    render(<InstanceHeader />, {wrapper: createWrapper()});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    singleInstanceDiagramStore.init();
    currentInstanceStore.init(mockInstanceWithActiveOperation.id);

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
  });

  it('should render instance data', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );
    render(<InstanceHeader />, {wrapper: createWrapper()});

    singleInstanceDiagramStore.init();
    currentInstanceStore.init(mockInstanceWithActiveOperation.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
    const {instance} = currentInstanceStore.state;

    const processName = getProcessName(instance);
    const instanceState = mockInstanceWithActiveOperation.state;

    expect(screen.getByText(processName)).toBeInTheDocument();
    expect(
      screen.getByText(mockInstanceWithActiveOperation.id)
    ).toBeInTheDocument();
    expect(
      screen.getByText(mockInstanceWithActiveOperation.processVersion)
    ).toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(screen.getByText('--')).toBeInTheDocument();
    expect(screen.getByTestId(`${instanceState}-icon`)).toBeInTheDocument();
    expect(screen.getByText('Process')).toBeInTheDocument();
    expect(screen.getByText('Instance Id')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();
    expect(screen.getByText('Start Date')).toBeInTheDocument();
    expect(screen.getByText('End Date')).toBeInTheDocument();
    expect(screen.getByText('Parent Instance Id')).toBeInTheDocument();
    expect(screen.getByText('Called Instances')).toBeInTheDocument();
    expect(screen.getAllByText('None').length).toBe(2);
    expect(
      screen.queryByRole('link', {name: /view all/i})
    ).not.toBeInTheDocument();
  });

  it('should render "View All" link for call activity process', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      )
    );

    render(<InstanceHeader />, {wrapper: createWrapper()});

    singleInstanceDiagramStore.init();
    currentInstanceStore.init(mockInstanceWithActiveOperation.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
    expect(
      await screen.findByRole('link', {name: /view all/i})
    ).toBeInTheDocument();
  });

  it('should navigate to Instances Page and expand Filters Panel on "View All" click', async () => {
    panelStatesStore.toggleFiltersPanel();

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      )
    );

    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/instances/instance_1/'],
    });

    render(<InstanceHeader />, {wrapper: createWrapper(MOCK_HISTORY)});

    singleInstanceDiagramStore.init();
    currentInstanceStore.init(mockInstanceWithActiveOperation.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(MOCK_HISTORY.location.pathname).toBe('/instances/instance_1/');
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    userEvent.click(await screen.findByRole('link', {name: /view all/i}));

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should render parent instance id', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithParentInstance))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );
    render(<InstanceHeader />, {wrapper: createWrapper()});

    singleInstanceDiagramStore.init();
    currentInstanceStore.init(mockInstanceWithParentInstance.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.getByRole('link', {
        name: `View parent instance ${mockInstanceWithParentInstance.parentInstanceId}`,
      })
    ).toBeInTheDocument();
  });

  it('should show spinner based on instance having active operations', async () => {
    render(<InstanceHeader />, {wrapper: createWrapper()});

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithoutOperations))
      ),
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    jest.useFakeTimers();
    singleInstanceDiagramStore.init();
    currentInstanceStore.init(mockInstanceWithoutOperations.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    jest.runOnlyPendingTimers();
    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should show spinner when operation is applied', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithoutOperations))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json(mockOperationCreated))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    render(<InstanceHeader />, {wrapper: createWrapper()});

    singleInstanceDiagramStore.init();
    currentInstanceStore.init(mockInstanceWithoutOperations.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    userEvent.click(screen.getByRole('button', {name: /Cancel Instance/}));
    userEvent.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should show spinner when variables is updated', async () => {
    const mockVariable = {
      name: 'key',
      value: 'value',
      hasActiveOperation: false,
    };

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithoutOperations))
      ),
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json([mockVariable]))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json(undefined))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    render(<InstanceHeader />, {wrapper: createWrapper()});
    singleInstanceDiagramStore.init();
    currentInstanceStore.init(mockInstanceWithActiveOperation.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    variablesStore.addVariable({
      id: mockInstanceWithoutOperations.id,
      name: mockVariable.name,
      value: mockVariable.value,
      onSuccess: () => {},
      onError: () => {},
    });

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: mockInstanceWithActiveOperation.id,
      payload: {pageSize: 10, scopeId: '1'},
    });

    await waitForElementToBeRemoved(screen.queryByTestId('operation-spinner'));
  });

  it('should remove spinner when operation fails', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithoutOperations))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({error: 'an error occured'}))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );
    render(<InstanceHeader />, {wrapper: createWrapper()});
    singleInstanceDiagramStore.init();
    currentInstanceStore.init(mockInstanceWithoutOperations.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    userEvent.click(screen.getByRole('button', {name: /Cancel Instance/}));
    userEvent.click(screen.getByRole('button', {name: /Apply/}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));
  });

  it('should show operation buttons when user has permission', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read', 'write'],
      canLogout: true,
    });

    render(<InstanceHeader />, {wrapper: createWrapper()});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    singleInstanceDiagramStore.init();
    currentInstanceStore.init(mockInstanceWithActiveOperation.id);

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.getByRole('button', {name: /Cancel Instance/})
    ).toBeInTheDocument();
  });

  it('should hide operation buttons when user has no permission', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
    });

    render(<InstanceHeader />, {wrapper: createWrapper()});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    singleInstanceDiagramStore.init();
    currentInstanceStore.init(mockInstanceWithActiveOperation.id);

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.queryByRole('button', {name: /Cancel Instance/})
    ).not.toBeInTheDocument();
  });

  it('should display notification and redirect if delete operation is performed', async () => {
    jest.useFakeTimers();
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockCanceledInstance))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/instances/instance_1/'],
    });

    const InstanceHeaderComponent = () => {
      return <InstanceHeader />;
    };

    render(<InstanceHeaderComponent />, {wrapper: createWrapper(MOCK_HISTORY)});

    singleInstanceDiagramStore.init();

    const notifications = useNotifications();
    currentInstanceStore.init(
      mockInstanceWithoutOperations.id,
      MOCK_HISTORY,
      notifications
    );
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(MOCK_HISTORY.location.pathname).toBe('/instances/instance_1/');
    userEvent.click(screen.getByRole('button', {name: /Delete Instance/}));
    expect(screen.getByText(/About to delete Instance/)).toBeInTheDocument();

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );

    userEvent.click(screen.getByTestId('delete-button'));
    await waitForElementToBeRemoved(
      screen.getByText(/About to delete Instance/)
    );

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.status(404), ctx.json({}))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(notifications.displayNotification).toHaveBeenCalledWith(
        'success',
        {
          headline: 'Instance deleted',
        }
      )
    );

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
