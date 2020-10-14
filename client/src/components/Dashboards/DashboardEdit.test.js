/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {nowDirty} from 'saveGuard';
import {EntityNameForm} from 'components';
import {showPrompt} from 'prompt';
import {redirectTo} from 'redirect';

import {FiltersEdit} from './filters';

import DashboardEdit from './DashboardEdit';

jest.mock('saveGuard', () => ({nowDirty: jest.fn(), nowPristine: jest.fn()}));
jest.mock('prompt', () => ({
  showPrompt: jest.fn().mockImplementation(async (config, cb) => await cb()),
}));
jest.mock('redirect', () => ({
  redirectTo: jest.fn(),
}));

beforeEach(() => {
  showPrompt.mockClear();
  redirectTo.mockClear();
});

it('should contain an AddButton', () => {
  const node = shallow(<DashboardEdit />);

  expect(node.find('AddButton')).toExist();
});

it('should contain editing report addons', () => {
  const node = shallow(<DashboardEdit />);

  expect(node.find('DashboardRenderer').prop('addons')).toMatchSnapshot();
});

it('should pass the isNew prop to the EntityNameForm', () => {
  const node = shallow(<DashboardEdit isNew />);

  expect(node.find(EntityNameForm).prop('isNew')).toBe(true);
});

it('should notify the saveGuard of changes', () => {
  const node = shallow(<DashboardEdit initialReports={[]} />);

  node.setState({reports: ['someReport']});

  expect(nowDirty).toHaveBeenCalled();
});

it('should react to layout changes', () => {
  const node = shallow(
    <DashboardEdit
      initialReports={[
        {
          id: '1',
          position: {x: 0, y: 0},
          dimensions: {height: 2, width: 2},
        },
        {
          id: '2',
          position: {x: 3, y: 0},
          dimensions: {height: 4, width: 3},
        },
      ]}
    />
  );

  node.find('DashboardRenderer').prop('onChange')([
    {x: 0, y: 0, h: 4, w: 2},
    {x: 3, y: 2, h: 4, w: 3},
  ]);

  expect(node.state('reports')).toMatchSnapshot();
});

it('should have a toggleable Filters Edit section', () => {
  const node = shallow(<DashboardEdit initialReports={[]} />);

  expect(node.find(FiltersEdit)).not.toExist();

  node.find(EntityNameForm).find('.tool-button').simulate('click');

  expect(node.find(FiltersEdit)).toExist();
});

it('should shows Filters Edit by default if there are initial filters defined', () => {
  const node = shallow(<DashboardEdit initialAvailableFilters={[{}]} />);

  expect(node.find(FiltersEdit)).toExist();
});

it('should save the dashboard when going to the report edit mode', async () => {
  const report = {
    position: {x: 0, y: 0},
    dimensions: {height: 2, width: 2},
    report: {id: 'new'},
  };
  const spy = jest.fn();

  const node = shallow(<DashboardEdit initialReports={[report]} saveChanges={spy} />);

  node.find('DashboardRenderer').prop('addons')[2].props.onClick(report);

  await flushPromises();

  // Parent component takes care of saving the reports and assigning ids
  node.setProps({
    initialReports: [
      {
        position: {x: 0, y: 0},
        dimensions: {height: 2, width: 2},
        id: '1',
      },
    ],
  });

  await flushPromises();

  expect(spy).toHaveBeenCalled();
  expect(redirectTo).toHaveBeenCalledWith('report/1/edit');
});
