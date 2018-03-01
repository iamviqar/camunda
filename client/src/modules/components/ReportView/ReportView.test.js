import React from 'react';
import {mount} from 'enzyme';

import ReportView from './ReportView';
import {Number, Table} from './views';

jest.mock('./views', () => {return {
  Number: props => <div>Number: {props.data}</div>,
  Table: props => <div> Table: {JSON.stringify(props.data)}</div>,
  Json: props => <div>JSON: {JSON.stringify(props.data)}</div>
}});

jest.mock('components', () => {return {
  ErrorBoundary: (props) => <div>{props.children}</div>
}});

jest.mock('services', () => {return {
  reportLabelMap: {
    objectToLabel: () => 'foo',
    objectToKey: () => 'foo',
    keyToLabel: () => 'foo',
    getOptions: () => [],
    keyToObject: () => 'foo',
  },
  getFlowNodeNames: jest.fn().mockReturnValue({
    a: 'foo',
    b: 'bar'
  })
}});

jest.mock('./ReportBlankSlate', () => {return (props) => {
    return <div className='message'>{props.message}</div>;
  }
});

it('should display a number if visualization is number', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: 'bar'
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report} />);
  node.setState({
    loaded: true
  });
  expect(node).toIncludeText('Number: 1234');
});

it('should display a json if visualization is json', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: 'bar'
      },
      visualization: 'json'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);
  node.setState({
    loaded: true
  });
  expect(node).toIncludeText('JSON');
});

it('should provide an errorMessage property to the component', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: 'bar'
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);
  node.setState({
    loaded: true
  });
  expect(node.find(Number)).toHaveProp('errorMessage');
});

it('should instruct to add a process definition id if not available', () => {
  const report = {
    data: {
      processDefinitionId: '',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: 'bar'
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);
  node.setState({
    loaded: true
  });
  expect(node.find('.message')).toIncludeText('Process definition');
});

it('should instruct to add view option if not available', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: ''
      },
      groupBy : {
        type: 'bar'
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);
  node.setState({
    loaded: true
  });
  expect(node.find('.message')).toIncludeText('View');
});

it('should instruct to add group by option if not available', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: ''
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);
  node.setState({
    loaded: true
  });
  expect(node.find('.message')).toIncludeText('Group by');
});

it('should instruct to add visualization option if not available', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: 'bar'
      },
      visualization: ''
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);
  node.setState({
    loaded: true
  });
  expect(node.find('.message')).toIncludeText('Visualize as');
});

it('should not add instruction for group by if operation is raw data', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'rawData'
      },
      groupBy : {
        type: ''
      },
      visualization: 'number'
    },
    result: 1234
  }

  const node = mount(<ReportView report={report}/>);
  node.setState({
    loaded: true
  });
  expect(node).not.toIncludeText('Please choose an option for');
});

it('should adjust date shown in table to unit', () => {
  const report = {
    data: {
      processDefinitionId: '123',
      view : {
        operation: 'foo'
      },
      groupBy : {
        type: 'processInstance',
        unit: 'day'
      },
      visualization: 'table'
    },
    result: {
      '2015-03-25T12:00:00Z': 2,
      '2015-03-26T12:00:00Z': 3
    }
  }

  const node = mount(<ReportView report={report}/>);
  node.setState({
    loaded: true
  });
  expect(node.find(Table)).not.toIncludeText('2015-03-25T12:00:00Z');
  expect(node.find(Table)).toIncludeText('2015-03-25');
});
